## Copyright 2018,2019, Regents of the University of Lancaster
## All rights reserved.
## 
## Redistribution and use in source and binary forms, with or without
## modification, are permitted provided that the following conditions are
## met:
## 
##  * Redistributions of source code must retain the above copyright
##    notice, this list of conditions and the following disclaimer.
## 
##  * Redistributions in binary form must reproduce the above copyright
##    notice, this list of conditions and the following disclaimer in the
##    documentation and/or other materials provided with the
##    distribution.
## 
##  * Neither the name of the University of Lancaster nor the names of
##    its contributors may be used to endorse or promote products derived
##    from this software without specific prior written permission.
## 
## THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
## "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
## LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
## A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
## OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
## SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
## LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
## DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
## THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
## (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
## OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
## 
## 
## Author: Steven Simpson <s.simpson@lancaster.ac.uk>

## Tuple-sliced learning switch controller

## A single switch is sliced to behave like several, each with a
## discrete set of (port[, vlan[, inner-vlan]]) tuples.  When a slice
## has one tuple, traffic arriving on it is dropped.  When a slice has
## two tuples, E-Line behaviour is applied.  Otherwise, the slice is
## deemed 'multi-tuple', and a straight-forward learning switch is
## implemented.  Three regular OpenFlow tables and one group table are
## used.

## Every tuple that is part of a multi-tuple slice's set has a group
## id assigned to it, and it is referred to as that group's input
## tuple.  The number of buckets in the group is one less than the set
## size, and each bucket directs to one of the other tuples in the set
## (the output tuples of that group).  If the output tuple is (p,
## outer, inner), the bucket pushes a CTAG, sets the VLAN to inner,
## pushes an STAG, sets the VLAN to outer, and outputs on port p.  If
## the output tuple is (p, tag), the bucket pushes a CTAG, sets the
## VLAN to tag, and outputs on port p.  Otherwise, the output tuple is
## (p), and the bucket simply outputs on port p.  Exceptionally in all
## three cases, if p is the same port as specified in the input tuple,
## output is to IN_PORT rather than to p; otherwise, the bucket would
## be ignored.  On entry to T2, metadata is always set to identify a
## group, and the packet will have no VLAN tags that the switch is
## interested in.

## For every pair of group ids (ig: input group; og: output group), a
## low-priority rule is established in T2 matching (metadata=ig) =>
## (group: og).  These implement the flooding rules for each
## multi-tuple slice.  A slice with n tuples will have n groups
## allocated to it, and n static rules outputting to these groups.

## T2's high-priority rules are established dynamically when a MAC
## address m is seen as source on a given tuple t which has an
## allocated group id, and is therefore part of a multi-tuple slice.
## For a slice of n tuples, n rules must be established for each new
## address.  Each one matches (metadata=ig, eth_dst=m), where ig is
## the group id assigned to each of the slice's tuples that isn't t.
## The actions are almost identical in each rule; each pushes
## CTAG(inner), STAG(outer) and outputting on p if the tuple is (p,
## outer, inner), pushing CTAG(tag) and outputting on p if the tuple
## is (p, tag), or simply outputting on p; each matches eth_dst=m.  As
## before, outputting to p is replaced by outputting to IN_PORT if p
## is the same port as for the tuple corresponding to group ig.
## Exceptionally, one of these rules identifies that the destination
## tuple is the same as the source tuple, and explicitly drops the
## packet.  Finally, the cookies of the n rules are set to
## (0x1000000000000000 | og), where og is the group corresponding to
## tuple t.  This is used to flush out stagnant dynamic rules
## outputting to t, as it is impossible to distinguish them via match
## conditions.

## For each learned MAC m on tuple it:
##
## * ig=group(it)
## 
## * For each ot in tuples(slice_of(t)):
##
## ** og=group(ot)
##
## ** P2 C=group(it) (metadata=ig, eth_dst=m) => out_actions(ot,
## ** it.port)
##
## For each group ig:
##
## * P1 C=-1 (metadata=ig) => goto_group(ig)

## T0's highest-priority rule drops untagged LLDP.  The next rules
## serve (port) tuples of multi-tuple slices by matching the port and
## known source MAC addresses, setting the metadata to the tuple's
## group id, and submitting to T2.  The next rules with the same
## priority serve three different cases: (1) For (port) tuples of
## 2-tuple slices, match the port, and deliver to the corresponding
## tuple (which may entail pushing one or two tags, and sending to the
## output or input port).  (2) For (port) tuples of multi-tuple
## slices, match the port, and send to the controller.  (3) For (port,
## vlan) and (port, vlan, inner) tuples of multi-tuple slices, match
## port and vlan, then pop vlan, set it as metadata, and submit to T1.

## P6 (no-vlan, LLDP) => drop
##
## For each learned MAC m on tuple it(port):
##
## * P5 C=group(it) (in_port=port, eth_src=m) => metadata=group(it),
## * T2
##
## For each slice s:
##
## * If count(tuples(s)) > 2:
##
## ** For each it(port, vlan) or (port, vlan, inner) of tuples(s):
##
## *** P4 C=group(it) (in_port=port, vlan=vlan) => pop-vlan,
## *** metadata=vlan, T1
##
## ** For each it(port) of tuples(s):
##
## *** P4 C=group(it) (in_port=port) => controller
##
## * If count(tuples(s)) == 2:
##
## ** For each it(port) of tuples(s):
##
## *** ot=other tuple of s
##
## *** P4 (in_port=port) => out_actions(ot, port)

## On entry to T1, the metadata holds the value of a popped tag, so T1
## only serves tuples of the forms (port, vlan) and (port, vlan,
## inner).  Its highest-priority rules serve multi-tuple slices, and
## match known source MACs, as well as the metadata against vlan for
## (port, vlan) and (port, vlan, inner) tuples, and vlan against inner
## for the latter, set the metadata to the tuple's group id, pop the
## vlan tag in the latter case, and submit to T2.  The remaining rules
## match metadata against vlan for (port, vlan) and (port, vlan,
## inner) tuples, and vlan against inner for the latter, set the
## metadata to the tuple's group id, pop the vlan tag in the latter
## case, then then either re-tag and deliver to a port for 2-tuple
## slices, or set the metadata to the tuple's group id, and deliver to
## the controller.

## For each learned MAC m on tuple it(port, vlan)
##
## * P5 C=group(it) (in_port=port, metadata=vlan, eth_src=m) =>
## * metadata=group(it), T2
##
## For each learned MAC m on tuple it(port, vlan, inner)
##
## * P5 C=group(it) (in_port=port, metadata=vlan, vlan=inner,
## * eth_src=m) => pop-vlan, metadata=group(it), T2
##
## For each slice s:
##
## * If count(tuples(s)) > 2:
##
## ** For each it(port, vlan) or of tuples(s):
##
## *** P4 C=group(it) (in_port=port, metadata=vlan) => controller
##
## ** For each it(port, vlan, inner) of tuples(s):
##
## *** P4 C=group(it) (in_port=port, metadata=vlan, vlan=inner) =>
## *** controller
##
## * If count(tuples(s)) == 2:
##
## ** For each it(port, vlan) of tuples(s):
##
## *** ot=other tuple of s
##
## *** P4 (in_port=port, metadata=vlan) => out_actions(ot, port)
##
## ** For each it(port, vlan, inner) of tuples(s):
##
## *** ot=other tuple of s
##
## *** P4 (in_port=port, metadata=vlan, vlan=inner) => pop-vlan,
## *** out_actions(ot, port)

## The controller implements a REST interface allowing any number of
## tuple sets to be specified.  The controller retains all tuple set
## information, and if any new sets intersect with old ones, rules for
## all old ones are revised.  Additionally, new tuples can cancel out
## existing ones even if they have different levels of encapsulation.
## For example, the addition of a tuple (6) will cancel tuples on any
## slice of the form (6, *) and (6, *, *).  (6, 100) will cancel (6)
## and (6, *, *).  (6, 100, 200) will cancel (6), and (6, *).  This
## exclusivity is necessary because OpenFlow does not (appear to?)
## have a way to distinguish stags from ctags.  The necessity depends
## on who adds tags, the user or the system.  If the system added
## either stag-ctag or ctag, and these system tags could be
## distinguished from user tags, which would always appear after the
## first ctag, and would be opaque to the switch.  As they can't be
## distinguished, the switch must know exactly how many tags have been
## added by the system at the remote end, so the addition of (6, 100,
## 200) is taken as replacement knowledge that port 6 is
## double-tagged, and automatically invalidates prior knowledge
## implied by (say) (6, 100), i.e., that 6 is single-tagged.  If tags
## could be distinguished, (6, 100) and (6, 100, 200) could be used
## simultaneously even if the user adds their own tags.  However, even
## in that case, (6) cannot be used with (6, 100) or (6, 100, 200),
## unless one guarantees that (6) traffic has no user tags.

import logging
import json

from ryu.base import app_manager
from ryu.controller import ofp_event
from ryu.controller import dpset
from ryu.controller.handler import MAIN_DISPATCHER
from ryu.controller.handler import set_ev_cls
from ryu.ofproto import ofproto_v1_3
from ryu.ofproto import ether
from ryu.lib.packet import packet
from ryu.lib.packet import ethernet
from ryu.lib.packet import ether_types
from ryu.app.ofctl import api
from ryu.app.wsgi import ControllerBase, WSGIApplication, route
from ryu.lib import dpid as dpid_lib
from webob import Response

LOG = logging.getLogger(__name__)

tuple_slicer_instance_name = 'slicer_api_app'
url = '/slicer/api/v1/config/{dpid}'

def tuples_text(tups):
    r = ''
    s = ''
    for tup in tups:
        r += s
        s = ', '
        r += tuple_text(tup)
    return r

def tuple_text(tup):
    r = '%d' % tup[0]
    if len(tup) > 1:
        r += '.%d' % tup[1]
        if len(tup) > 2:
            r += '.%d' % tup[2]
    return r

def tuples_conflict(tup1, tup2):
    if tup1[0] != tup2[0]:
        return False
    if len(tup1) == 1:
        return True
    if len(tup2) == 1:
        return True
    if tup1[1] != tup2[1]:
        return False
    if len(tup1) == 2:
        return True
    if len(tup2) == 2:
        return True
    return tup1[2] == tup2[2]

class Slice:
    def __init__(self, outer):
        self.switch = outer

        ## The set of tuples that should be part of this slice
        self.target = set()

        ## The set of tuples that have the necessary rules to be part
        ## of this slice
        self.established = set()

        ## Keep track of the tuple on which a MAC was last seen.
        self.mac_tup = { }

    def see(self, mac, tup):
        oldtup = self.mac_tup.get(mac)
        self.mac_tup[mac] = tup
        return oldtup

    def unsee(self, mac, tup):
        if tup in self.target:
            self.mac_tup.pop(mac, None)

    def lookup(self, mac):
        tup = self.mac_tup.get(mac)
        if tup is None or tup not in self.established:
            return None
        return tup

    def get_tuples(self):
        return frozenset(self.target)

    def sanitize(self):
        ## Reduce the target set of tuples to those with ports that
        ## actually exist, while retaining the intended target.
        self.sanitized = set()
        for tup in self.target:
            if tup[0] not in self.switch.known_ports:
                continue
            self.sanitized.add(tup)
        self.sanitized = frozenset(self.sanitized)
        return

    def match(self):
        self.established = set(self.sanitized)

    def invalidate(self):
        ## Make it look as if we had no tuples, and then had several
        ## suddenly added, and that this new set is what we've got
        ## now.
        self.established.clear()
        self.switch.invalid_slices.add(self)

    ## Ensure that this slice has the right set of static rules, based
    ## on its tuple set.  If the number of tuples is greater than two,
    ## each tuple needs a group entry, outputting to all other tuples
    ## in the set.  If there are exactly two tuples, two E-Line rules
    ## must exist, exchanging traffic between them.  If there is one
    ## tuple, a single rule is required to explicitly drop the
    ## traffic.  This function only deletes rules, and releases
    ## groups.  Use add_static_rules to create rules and allocate
    ## groups.
    def delete_static_rules(self):
        if self.sanitized == self.established:
            ## Nothing has actually changed.
            return

        dp = self.switch.datapath
        ofp = dp.ofproto
        parser = dp.ofproto_parser
        LOG.info("%016x: %s -> %s", dp.id,
                 tuples_text(self.established), tuples_text(self.sanitized))

        ## Work out which tuples are now invalid.
        if len(self.established) == 2:
            oldtups = self.established
        elif len(self.sanitized) <= 2:
            oldtups = self.established
        else:
            oldtups = self.established - self.sanitized

        for tup in oldtups:
            LOG.info("%016x: deleting rules for %s", dp.id,
                     tuple_text(tup))

            ## Delete T0/T1 rules identifying the source.
            self.switch.invalidate_first_tag_rule(tup)
            (match, tbl, prio) = self.switch.tuple_match(tup)
            group = self.switch.get_group_for_tuple(tup)
            if group is None:
                cookie = 0
                cookie_mask = 0
                out_port = ofp.OFPP_ANY
            else:
                cookie = group
                cookie_mask = 0xffffffffffffffff
                out_port = ofp.OFPP_CONTROLLER
            msg = parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                    cookie=cookie,
                                    cookie_mask=cookie_mask,
                                    datapath=dp,
                                    table_id=tbl,
                                    match=match,
                                    buffer_id=ofp.OFPCML_NO_BUFFER,
                                    out_port=out_port,
                                    out_group=ofp.OFPG_ANY)
            dp.send_msg(msg)


        if len(self.sanitized) <= 2 and len(self.established) > 2:
            ## Each tuple should have a group, but doesn't need it any
            ## more.  Release it.
            for tup in oldtups:
                group = self.switch.release_group_by_tuple(tup)
                if group is None:
                    continue
                assert group is not None

                LOG.info("%016x: deleting group %d for %s", dp.id,
                         group, tuple_text(tup))

                ## Remove the group definition from the switch table,
                ## automatically deleting the destination rule that
                ## directs to it.
                msg = parser.OFPGroupMod(datapath=dp,
                                         command=ofp.OFPGC_DELETE,
                                         group_id=group)
                dp.send_msg(msg)

                ## Remove the T2 rules that match the group and a
                ## destination MAC address.
                match = parser.OFPMatch(metadata=group)
                msg = parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                        cookie=0xffffffffffffffff,
                                        datapath=dp,
                                        table_id=2,
                                        match=match,
                                        buffer_id=ofp.OFPCML_NO_BUFFER,
                                        out_port=ofp.OFPP_ANY,
                                        out_group=ofp.OFPG_ANY)
                dp.send_msg(msg)

    ## Ensure that this slice has the right set of static rules, based
    ## on its tuple set.  If the number of tuple is greater than two,
    ## a group entry is needed per tuple, outputting to all other
    ## tuples in the set.  If there are exactly two tuples, two E-Line
    ## rules must exist, exchanging traffic between them.  This
    ## function only adds rules, and allocates groups.  Use
    ## delete_static_rules to delete rules and release groups.
    def add_static_rules(self):
        ## Get meters for all tuples.
        outmtrs = {}
        inmtrs = {}
        for tup in self.sanitized:
            self.switch.get_meters(tup, inmtrs, outmtrs)

        if self.sanitized == self.established:
            ## Nothing has actually changed.
            return

        dp = self.switch.datapath
        ofp = dp.ofproto
        parser = dp.ofproto_parser

        ## A slice with fewer than 2 tuples should have no OpenFlow
        ## manifestations.  The default drop rule should apply.
        if len(self.sanitized) < 2:
            return

        if len(self.sanitized) == 2:
            ## We have two tuples now, and we either didn't have
            ## exactly two before, or at least one has changed (and
            ## we've already deleted the old rules).  Create E-Line
            ## rules.
            tups = list(self.sanitized)
            for i in [ 0, 1 ]:
                self.switch.ensure_first_tag_rule(tups[i])
                LOG.info("%016x: adding e-line for %s->%s", dp.id,
                         tuple_text(tups[i]), tuple_text(tups[1-i]))
                (match, tbl, prio) = self.switch.tuple_match(tups[i])
                actions = self.switch.tuple_action(tups[1-i], tups[i][0])
                inst = [parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                     actions)]
                ## Apply ingress (i) and egress meters (1-i).
                if tups[1-i] in outmtrs:
                    inst.insert(0,
                                parser.OFPInstructionMeter(outmtrs[tups[1-i]]))
                if tups[i] in inmtrs:
                    inst.insert(0, parser.OFPInstructionMeter(inmtrs[tups[i]]))
                msg = parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                        datapath=dp,
                                        table_id=tbl,
                                        priority=prio,
                                        match=match,
                                        instructions=inst)
                dp.send_msg(msg)
            return

        ## Identify the set of new tuples.
        if len(self.established) <= 2:
            newports = self.sanitized
        else:
            newports = self.sanitized - self.established

        ## We have full learning switch behaviour.  Ensure that
        ## each tuple's group points to each of the other tuples
        ## in the slice.
        for stup in self.sanitized:
            ## Ensure that this tuple has a group.  Create/modify the
            ## group entry in the switch with all the tuples in the
            ## target set, except for the one represented by the
            ## source tuple.
            (group, added) = self.switch.claim_group_for_tuple(stup)
            LOG.info("%016x: updating group %d tuple %s->%s", dp.id,
                     group, tuple_text(stup),
                     tuples_text(self.sanitized - set([stup])))
            cmd = ofp.OFPGC_ADD if added else ofp.OFPGC_MODIFY
            buckets = []
            for dtup in self.sanitized:
                if dtup == stup:
                    continue
                actions = self.switch.tuple_action(dtup, stup[0])
                ## Apply egress meter to this bucket.
                if hasattr(parser, 'OFPActionMeter') and dtup in outmtrs:
                    actions.insert(0, parser.OFPActionMeter(outmtrs[dtup]))
                buckets.append(parser.OFPBucket(actions=actions))
            msg = parser.OFPGroupMod(datapath=dp,
                                     command=cmd,
                                     type_=ofp.OFPGT_ALL,
                                     group_id=group,
                                     buckets=buckets)
            dp.send_msg(msg)

            if added:
                ## Make sure that unknown destinations in this slice
                ## are broadcast to the group.  We match the source by
                ## the metadata set to the group id prior to entry of
                ## T2.  This rule will automatically be deleted when
                ## the group is deleted, because it refers to the
                ## group in its actions.
                match = parser.OFPMatch(metadata=group)
                actions = [parser.OFPActionGroup(group)]
                inst = [parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                     actions)]
                ## Apply all egress meters, except for the source
                ## tuple.
                if not hasattr(parser, 'OFPActionMeter'):
                    for ctup in self.sanitized:
                        if ctup not in outmtrs:
                            continue
                        if ctup == stup:
                            continue
                        inst.insert(0,
                                    parser.OFPInstructionMeter(outmtrs[ctup]))
                msg = parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                        cookie=0xffffffffffffffff,
                                        datapath=dp,
                                        table_id=2,
                                        priority=1,
                                        match=match,
                                        instructions=inst)
                dp.send_msg(msg)

        for stup in newports:
            ## Packets from this tuple with unrecognized source MACs
            ## are to go to the controller.
            group = self.switch.get_group_for_tuple(stup)
            (match, tbl, prio) = self.switch.tuple_match(stup)
            actions = [parser.OFPActionOutput(ofp.OFPP_CONTROLLER, 65535)]
            inst = [parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                 actions)]
            ## Apply an ingress meter.
            if stup in inmtrs:
                inst.insert(0, parser.OFPInstructionMeter(inmtrs[stup]))
            msg = parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                    cookie=group,
                                    datapath=dp,
                                    table_id=tbl,
                                    priority=prio,
                                    match=match,
                                    instructions=inst)
            dp.send_msg(msg)
            self.switch.ensure_first_tag_rule(stup)


    ## Ensure that a tuple belongs to this slice.  If it belongs to
    ## something else, get that to abandon it.
    def adopt(self, tup):
        ## Make no changes if we already have this tuple.
        if tup in self.target:
            return

        ## Check all tuples of all slices for conflicts.  Force slices
        ## with those tuples to abandon them.
        abd = []
        for tup2, slize in self.switch.target_index.iteritems():
            if tuples_conflict(tup, tup2):
                if tup == tup2 and slize == self:
                    continue
                abd.append((slize, tup2))
        for en in abd:
            en[0].abandon(en[1])

        ## Record that this slice owns this tuple now, and that we
        ## need to be revalidated.
        self.target.add(tup)
        self.switch.target_index[tup] = self
        self.switch.invalid_slices.add(self)

    def abandon(self, tup):
        ## Make no changes if we already don't have this tuple.
        if tup not in self.target:
            return

        ## Record that this slice no longer owns this tuple now, and
        ## that we need to be revalidated.
        self.target.discard(tup)
        us = self.switch.target_index.pop(tup)
        assert us == self
        self.switch.invalid_slices.add(self)
        return

class SwitchStatus:
    def __init__(self):
        self.datapath = None

        ## Keep a set of ports known to belong to the switch.
        self.known_ports = set()

        ## These groups and anything after the largest value are free.
        self.free_groups = set([ 0 ])
        ## Record the tuple-group mapping in both directions.
        self.group_to_tuple = { }
        self.tuple_to_group = { }

        ## Record the meter pair for each tuple.  The value N means
        ## that the tuple should user meter 2*N for ingress and 2*N+1
        ## for egress.
        self.free_meters = set([ 0 ])
        self.inmeters = { }
        self.outmeters = { }

        ## tuple -> Slice
        self.target_index = { }

        ## Keep track of slices that might be out-of-date.
        self.invalid_slices = set()

        ## Keep track of (port, vlan) rules that might have become
        ## redundant.
        self.invalid_first_tag_rules = set()

    def set_datapath(self, dp):
        self.datapath = dp
        self.known_ports = set()

    ## Create a match for a rule in either T0/T1 implementing
    ## E-Line/drop rules.  Return the match, which table it goes in,
    ## and what priority to use.
    def tuple_match(self, tup, mac=None):
        dp = self.datapath
        parser = dp.ofproto_parser
        if len(tup) == 1:
            if mac is None:
                return (parser.OFPMatch(in_port=tup[0]), 0, 4)
            else:
                return (parser.OFPMatch(in_port=tup[0], eth_src=mac), 0, 5)
        if len(tup) == 2:
            if mac is None:
                return (parser.OFPMatch(in_port=tup[0],
                                        metadata=tup[1]), 1, 4)
            else:
                return (parser.OFPMatch(in_port=tup[0],
                                        eth_src=mac,
                                        metadata=tup[1]), 1, 5)
        if mac is None:
            return (parser.OFPMatch(in_port=tup[0],
                                    metadata=tup[1],
                                    vlan_vid=0x1000|tup[2]), 1, 4)
        else:
            return (parser.OFPMatch(in_port=tup[0],
                                    eth_src=mac,
                                    metadata=tup[1],
                                    vlan_vid=0x1000|tup[2]), 1, 5)

    ## Create an action list for output to a particular tuple.  If the
    ## port of the tuple is the same as a given input port, explicitly
    ## output to IN_PORT, rather than the tuple's port (or the bucket
    ## will be ignored).  This kind of action list is used as buckets
    ## in the group table, and for learned rules in T2.
    def tuple_action(self, tup, in_port):
        dp = self.datapath
        ofp = dp.ofproto
        parser = dp.ofproto_parser
        out_port = ofp.OFPP_IN_PORT if tup[0] == in_port else tup[0]
        if len(tup) == 1:
            return [parser.OFPActionOutput(out_port)]
        if len(tup) == 2:
            return [parser.OFPActionPushVlan(ether.ETH_TYPE_8021Q), \
                    parser.OFPActionSetField(vlan_vid=0x1000|tup[1]), \
                    parser.OFPActionOutput(out_port)]
        return [parser.OFPActionPushVlan(ether.ETH_TYPE_8021Q), \
                parser.OFPActionSetField(vlan_vid=0x1000|tup[2]), \
                parser.OFPActionPushVlan(ether.ETH_TYPE_8021AD), \
                parser.OFPActionSetField(vlan_vid=0x1000|tup[1]), \
                parser.OFPActionOutput(out_port)]

    def get_config(self):
        result = []
        for slize in set(self.target_index.values()):
            result.append(list(slize.get_tuples()))
        return result

    ## Look up a slice by one of its tuples.
    def get_slice(self, tup):
        return self.target_index.get(tup)

    def update_rates(self, tups, inrates, outrates):
        ## Ensure we have up-to-date meters for the given tuples.
        for tup in tups:
            self.set_inmeter(tup, inrates.get(tup))
            self.set_outmeter(tup, outrates.get(tup))
        return

    def create_slice(self, tups, inrates={}, outrates={}):
        tups = frozenset(tups)
        if len(tups) == 0:
            return None

        ## Check for invalid tuples.
        for tup in tups:
            ## Valid tuples have 1-3 elements.
            if len(tup) < 1:
                return None
            if len(tup) > 3:
                return None

            ## Valid tuples have no negative elements.
            if tup[0] < 0:
                return None
            if len(tup) > 1 and tup[1] < 0:
                return None
            if len(tup) > 2 and tup[2] < 0:
                return None

            ## Check for conflicts with other tuples.
            for otup in tups:
                if tup is otup:
                    continue
                if tuples_conflict(tup, otup):
                    return None

        ## Ensure meters exist for all tuples that have specified rates.
        self.update_rates(tups, inrates, outrates)

        ## Find a slice with the maximum overlap.
        best_slize = None
        best_overlap = 0
        for tup in tups:
            slize = self.target_index.get(tup)
            if slize is None:
                continue
            overlap = len(slize.get_tuples() & tups)
            if overlap > best_overlap:
                best_overlap = overlap
                best_slize = slize

        if best_slize is not None:
            ## Modify the slice with the maximum overlap, and create
            ## another slice with the remaining tuples.
            for tup in tups - best_slize.get_tuples():
                best_slize.adopt(tup)
            abandoned = best_slize.get_tuples() - tups
            other_slize = Slice(self)
            for tup in abandoned:
                other_slize.adopt(tup)
            return best_slize

        ## No overlapping slice was found, so create a brand-new one.
        slize = Slice(self)
        for tup in tups:
            slize.adopt(tup)
        return slize

    def get_meters(self, tup, inmtrs, outmtrs):
        if inmtrs is not None:
            inmtr = self.get_inmeter(tup)
            if inmtr is not None:
                inmtrs[tup] = inmtr
        if outmtrs is not None:
            outmtr = self.get_outmeter(tup)
            if outmtr is not None:
                outmtrs[tup] = outmtr
        return

    def get_inmeter(self, tup):
        tup = tuple(tup)
        return self.inmeters.get(tup)

    def get_outmeter(self, tup):
        tup = tuple(tup)
        return self.outmeters.get(tup)

    def set_inmeter(self, tup, rate):
        return self.set_meter(self.inmeters, tup, rate)

    def set_outmeter(self, tup, rate):
        return self.set_meter(self.outmeters, tup, rate)

    def drop_inmeter(self, tup):
        self.drop_meter(self.inmeters, tup)

    def drop_outmeter(self, tup):
        self.drop_meter(self.outmeters, tup)

    def drop_meter(self, mp, tup):
        tup = tuple(tup)
        dp = self.datapath
        ofp = dp.ofproto
        parser = dp.ofproto_parser

        mtr = mp.pop(tup)
        if mtr is None:
            return
        msg = parser.OFPMeterMod(datapath=dp,
                                 command=ofp.OFPMC_DELETE,
                                 flags=0,
                                 meter_id=mtr)
        dp.send_msg(msg)
        self.free_meters.add(mtr)
        return None

    def set_meter(self, mp, tup, rate):
        ## TODO: Detect lack of implementation of meters.
        if True:
            return None
        tup = tuple(tup)
        dp = self.datapath
        ofp = dp.ofproto
        parser = dp.ofproto_parser

        mtr = mp.get(tup)
        if mtr is None:
            ## A meter must be allocated.
            mtr = min(self.free_meters)
            self.free_meters.discard(mtr)
            if len(self.free_meters) == 0:
                self.free_meters.add(mtr + 1)
            cmd = ofp.OFPMC_ADD
            mp[tup] = mtr
        else:
            cmd = ofp.OFPMC_MODIFY
        if rate is None:
            ## Set a practically unlimited rate.  TODO: Experiment
            ## with an empty list.
            bands = [parser.OFPMeterBandDrop(type_=ofp.OFPMBT_DROP,
                                             len_=0,
                                             rate=0x7fffffff,
                                             burst_size=0x7fffffff)]
            msg = parser.OFPMeterMod(datapath=dp,
                                     command=cmd,
                                     flags=ofp.OFPMF_PKTPS,
                                     meter_id=mtr,
                                     bands=bands)
        else:
            ## TODO: Work out proper values for burst_size.
            bands = [parser.OFPMeterBandDrop(type_=ofp.OFPMBT_DROP,
                                             len_=0,
                                             rate=rate,
                                             burst_size=rate / 10)]
            msg = parser.OFPMeterMod(datapath=dp,
                                     command=cmd,
                                     flags=ofp.OFPMF_KBPS,
                                     meter_id=mtr,
                                     bands=bands)
        dp.send_msg(msg)
        return mtr

    ## Get the group id for a given tuple, without attempting to
    ## allocate one if not already allocated.
    def get_group_for_tuple(self, tup):
        return self.tuple_to_group.get(tup)

    ## Get or claim a group id for a given tuple.
    def claim_group_for_tuple(self, tup):
        tup = tuple(tup)
        ## Already allocated?
        if self.tuple_to_group.has_key(tup):
            return (self.tuple_to_group.get(tup), False)

        ## Get the lowest free group.
        group = min(self.free_groups)
        self.free_groups.discard(group)

        ## If we appear to have run out, just add back in one more
        ## than what we took.
        if len(self.free_groups) == 0:
            self.free_groups.add(group + 1)
        LOG.info("%016x: claiming group %d tuple %s",
                 self.datapath.id, group, tuple_text(tup))

        self.tuple_to_group[tup] = group;
        self.group_to_tuple[group] = tup;
        return (group, True)

    ## Release the group id allocated to a given tuple.  Return the
    ## formerly allocated group id.
    def release_group_by_tuple(self, tup):
        tup = tuple(tup)
        if tup not in self.tuple_to_group:
            return
        group = self.tuple_to_group.pop(tup)
        LOG.info("%016x: releasing group %d tuple %s",
                 self.datapath.id, group, tuple_text(tup))
        self.group_to_tuple.pop(group)
        self.free_groups.add(group)
        return group

    ## Release a given group id from its tuple.  Return the tuple it
    ## was allocated to.
    def release_group(self, group):
        tup = self.group_to_tuple.pop(group)
        if tup is None:
            return
        LOG.info("%016x: releasing group %d tuple %s",
                 self.datapath.id, group, tuple_text(tup))
        self.tuple_to_group.pop(tup)
        self.free_groups.add(group)
        return tup

    ## Record that a port was attached to the switch.
    def port_added(self, port):
        if port > 0x7fffffff:
            return
        dp = self.datapath
        LOG.info("%016x: gained port %d", dp.id, port)
        self.known_ports.add(port)
        return

    ## Record that a port was detached from the switch.  Remove tuples
    ## from slices that depend on that port.
    def port_removed(self, port):
        dp = self.datapath
        LOG.info("%016x: lost port %d", dp.id, port)
        self.known_ports.discard(port)

        ## Invalidate any slice targeted on that port.
        for item in self.target_index.items():
            tup = item[0]
            if tup[0] != port:
                continue
            slize = item[1]
            self.invalid_slices.add(slize)
        return

    ## Record that the user no longer wants to connect a tuple.
    def discard_tuple(self, tup):
        tup = tuple(tup)
        slize = self.target_index.get(tup)
        if slize is not None:
            slize.abandon(tup)
        return

    ## Invalidate all slices.
    def invalidate(self):
        for slize in set(self.target_index.values()):
            slize.invalidate()

    def delete_dynamic_rules(self, tup):
        dp = self.datapath
        ofp = dp.ofproto
        parser = dp.ofproto_parser

        ## Delete dynamic rules in T0/T1 matching this tuple and
        ## passing on to T1/T2.
        self.invalidate_first_tag_rule(tup)
        (match, tbl, prio) = self.tuple_match(tup)
        msg = parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                datapath=dp,
                                table_id=tbl,
                                match=match,
                                buffer_id=ofp.OFPCML_NO_BUFFER,
                                out_port=ofp.OFPP_ANY,
                                out_group=ofp.OFPG_ANY)
        dp.send_msg(msg)

        group = self.release_group_by_tuple(tup)
        if group is not None:
            ## Delete the group associated with the tuple.  This also
            ## deletes the rule matching that group and sending to
            ## that group as a broadcast.
            msg = parser.OFPGroupMod(datapath=dp,
                                     command=ofp.OFPGC_DELETE,
                                     group_id=group)
            dp.send_msg(msg)

            ## Delete dynamic rules in the destination table
            ## matching packets to the tuple.
            match = parser.OFPMatch()
            msg = parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                    cookie=group,
                                    cookie_mask=0xffffffffffffffff,
                                    datapath=dp,
                                    table_id=2,
                                    match=match,
                                    buffer_id=ofp.OFPCML_NO_BUFFER,
                                    out_port=ofp.OFPP_ANY,
                                    out_group=ofp.OFPG_ANY)
            dp.send_msg(msg)

            ## Delete dynamic rules in the destination table matching
            ## packets from the tuple.
            match = parser.OFPMatch(metadata=group)
            msg = parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                    datapath=dp,
                                    table_id=2,
                                    match=match,
                                    buffer_id=ofp.OFPCML_NO_BUFFER,
                                    out_port=ofp.OFPP_ANY,
                                    out_group=ofp.OFPG_ANY)
            dp.send_msg(msg)

    def revalidate(self):
        dp = self.datapath
        if dp is None:
            return
        LOG.info("%016x: revalidating...", dp.id)

        ## Identify all tuples that have been removed from their
        ## slices, and remove rules pertaining to them.
        tuples_to_reset = set()
        for inval in self.invalid_slices:
            inval_missing = inval.established.difference(inval.target)
            tuples_to_reset.update(inval_missing)
        for tup in tuples_to_reset:
            self.delete_dynamic_rules(tup)

        ## Work out the subset of target tuples that actually exist.
        for slize in self.invalid_slices:
            slize.sanitize()

        ## Ensure that each modified slice has the right static rules
        ## according to its target set.  This is done in two passes,
        ## one to delete rules and groups, and one to add them.
        for slize in self.invalid_slices:
            slize.delete_static_rules()
        for slize in self.invalid_slices:
            slize.add_static_rules()

        ## Make all established tuple sets match the targets.
        for slize in self.invalid_slices:
            slize.match()
        self.invalid_slices.clear()

        ## Clear redundant T0 rules that extract the first VLAN tag,
        ## store it as metadata, and pass on to T1.
        self.revalidate_first_tag_rules()

        ## De-allocate meters for dropped tuples.
        for tup in set(self.outmeters.keys()) - set(self.target_index.keys()):
            self.drop_outmeter(tup)
        for tup in set(self.inmeters.keys()) - set(self.target_index.keys()):
            self.drop_inmeter(tup)

        LOG.info("%016x: revalidating complete", dp.id)
        return

    ## Check that T0 contains no unnecessary rules matching (in_port,
    ## vlan_vid), popping the VLAN tag while saving it in the
    ## metadata, and passing on to T1 (to check for presence of a
    ## second tag).
    def revalidate_first_tag_rules(self):
        ## Check all slices to see if they require a T0 rule
        ## corresponding to the current set of candidates.  Remove
        ## matching candidates.
        for slize in set(self.target_index.values()):
            for tup in slize.get_tuples():
                self.invalid_first_tag_rules.discard(tup[0:2])

        dp = self.datapath
        ofp = dp.ofproto
        parser = dp.ofproto_parser

        ## Delete rules corresponding to the remaining candidates.
        for tup in self.invalid_first_tag_rules:
            port = tup[0]
            vlan = tup[1]
            match = parser.OFPMatch(in_port=port,
                                    vlan_vid=0x1000|vlan)
            msg = parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                    datapath=dp,
                                    table_id=0,
                                    match=match,
                                    buffer_id=ofp.OFPCML_NO_BUFFER,
                                    out_port=ofp.OFPP_ANY,
                                    out_group=ofp.OFPG_ANY)
            dp.send_msg(msg)

        ## Mark all candidates as investigated.
        self.invalid_first_tag_rules.clear()
        return

    ## Record that an (in_port, vlan_vid) rule in T0 might not be
    ## necessary any more.
    def invalidate_first_tag_rule(self, tup):
        if len(tup) < 2:
            return
        self.invalid_first_tag_rules.add(tup[0:2])
        return

    ## Ensure that a rule exists in T0 matching (port, vlan), saving
    ## the VLAN id in the metadata, popping the VLAN tag, and passing
    ## on to T1 (which will then check for a second tag).
    def ensure_first_tag_rule(self, tup):
        if len(tup) < 2:
            return
        port = tup[0]
        vlan = tup[1]

        dp = self.datapath
        ofp = dp.ofproto
        parser = dp.ofproto_parser

        match = parser.OFPMatch(in_port=port,
                                vlan_vid=0x1000|vlan)
        actions = [parser.OFPActionPopVlan(), \
                   parser.OFPActionSetField(metadata=vlan)]
        inst = [parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                             actions), \
                parser.OFPInstructionGotoTable(1)]
        msg = parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                datapath=dp,
                                table_id=0,
                                priority=4,
                                match=match,
                                instructions=inst)
        dp.send_msg(msg)
        return

class TupleSlicer(app_manager.RyuApp):
    _CONTEXTS = {'wsgi': WSGIApplication}
    OFP_VERSIONS = [ofproto_v1_3.OFP_VERSION]

    def __init__(self, *args, **kwargs):
        super(TupleSlicer, self).__init__(*args, **kwargs)

        ## DPID -> SwitchStatus
        self.switches = { }

        wsgi = kwargs['wsgi']
        wsgi.register(SliceController,
                      { tuple_slicer_instance_name: self })

    def _configure_set(self, dp, ports):
        ofp = dp.ofproto
        parser = dp.ofproto_parser

    @set_ev_cls(dpset.EventPortAdd, dpset.DPSET_EV_DISPATCHER)
    def port_added(self, ev):
        dp = ev.dp
        port = ev.port
        status = self.switches[dp.id]
        status.port_added(port.port_no)
        status.revalidate()
        return

    @set_ev_cls(dpset.EventPortDelete, dpset.DPSET_EV_DISPATCHER)
    def port_removed(self, ev):
        dp = ev.dp
        port = ev.port
        status = self.switches[dp.id]
        status.port_removed(port.port_no)
        status.revalidate()
        return

    @set_ev_cls(dpset.EventDP, dpset.DPSET_EV_DISPATCHER)
    def datapath_handler(self, ev):
        dp = ev.dp
        ofp = dp.ofproto
        parser = dp.ofproto_parser

        if not ev.enter:
            ## A switch has been detached.
            if dp.id not in self.switches:
                return
            status = self.switches[dp.id]
            status.set_datapath(None)
            return

        ## A switch has been attached.  Set up static flows.
        LOG.info("%016x: New switch", dp.id)

        ## Delete all meters.
        mymsg = parser.OFPMeterMod(datapath=dp,
                                   command=ofp.OFPMC_DELETE,
                                   flags=0,
                                   meter_id=ofp.OFPM_ALL)
        dp.send_msg(mymsg)

        ## Delete all flows in T0, T1, T2.
        match = parser.OFPMatch()
        for tbl in (0, 1, 2):
            mymsg = parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                      datapath=dp,
                                      table_id=tbl,
                                      buffer_id=ofp.OFPCML_NO_BUFFER,
                                      out_port=ofp.OFPP_ANY,
                                      out_group=ofp.OFPG_ANY,
                                      match=match)
            dp.send_msg(mymsg)

        ## Delete all groups.
        mymsg = parser.OFPGroupMod(datapath=dp,
                                   command=ofp.OFPGC_DELETE,
                                   group_id=ofp.OFPG_ALL)
        dp.send_msg(mymsg)

        ## Drop LLDP packets.
        match = parser.OFPMatch(vlan_vid=0x0000,
                                eth_type=ether.ETH_TYPE_LLDP)
        inst = [parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS, [])]
        mymsg = parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                  datapath=dp,
                                  table_id=0,
                                  priority=6,
                                  match=match,
                                  instructions=inst)
        dp.send_msg(mymsg)

        ## Mark all slices as invalid, then revalidate them.
        if dp.id not in self.switches:
            self.switches[dp.id] = SwitchStatus()
        status = self.switches[dp.id]
        status.set_datapath(dp)
        for p in ev.ports:
            status.port_added(p.port_no)
        status.invalidate()
        #status.create_slice([ (1,100), (2,), (1,101) ]) ## Test
        status.revalidate()
        #self._learn(dp, (2,), "54:e1:ad:4a:29:40", timeout=15) ## Test
        #self._learn(dp, (1,100), "54:e1:ad:4a:29:33", timeout=75) ## Test

    @set_ev_cls(ofp_event.EventOFPFlowRemoved, MAIN_DISPATCHER)
    def flow_removed_handler(self, ev):
        msg = ev.msg
        dp = msg.datapath
        ofp = dp.ofproto
        match = msg.match

        ## We only care about flows that have timed out.
        if msg.reason != ofp.OFPRR_IDLE_TIMEOUT:
            return

        if msg.table_id == 0:
            tup = (match['in_port'],)
        else:
            if 'vlan_vid' not in match:
                tup = (match['in_port'], match['metadata'])
            else:
                tup = (match['in_port'],
                       match['metadata'],
                       match['vlan_vid'] & 0xfff)
        ## We've not seen a packet from this MAC on its last tuple
        ## for a while.
        self._not_heard_from(dp, tup, match['eth_src'])

    def _not_heard_from(self, dp, tup, mac):
        ofp = dp.ofproto
        parser = dp.ofproto_parser
        status = self.switches[dp.id]
        slize = status.get_slice(tup)
        tups = slize.get_tuples()
        group = status.get_group_for_tuple(tup)

        LOG.info("%016x: %s/G%03d/%17s not heard from",
                 dp.id, tuple_text(tup), group, mac)

        slize.unsee(mac, tup)

        ## Delete unicast rules from the destination table (2).
        match = parser.OFPMatch(eth_dst=mac)
        msg = parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                cookie=group,
                                cookie_mask=0xffffffffffffffff,
                                datapath=dp,
                                table_id=2,
                                match=match,
                                buffer_id=ofp.OFPCML_NO_BUFFER,
                                out_port=ofp.OFPP_ANY,
                                out_group=ofp.OFPG_ANY)
        dp.send_msg(msg)
        return

    def _learn(self, dp, tup, mac, timeout=600):
        if dp is None:
            return
        LOG.info("%016x: %17s new on %s",
                 dp.id, mac, tuple_text(tup))
        status = self.switches[dp.id]
        status.revalidate()

        ## Is this tuple allocated to a slice?
        slize = status.get_slice(tup)
        if slize is None:
            return

        ofp = dp.ofproto
        parser = dp.ofproto_parser

        tups = slize.get_tuples()
        group = status.get_group_for_tuple(tup)
        if group is None:
            return

        slize.see(mac, tup)

        ## Get all necessary meters.
        outmtr = status.get_inmeter(tup)
        inmtr = status.get_outmeter(tup)

        ## Add rules in T2 to prevent flooding for this MAC address.
        ## Using the cookie, label the rule with the tuple's group,
        ## since we have no way in general to match the actions when
        ## we want to delete these rules.
        for stup in tups:
            sgroup = status.get_group_for_tuple(stup)
            match = parser.OFPMatch(metadata=sgroup, eth_dst=mac)
            ## Drop if this packet would be forwarded to its input.
            actions = [] if group == sgroup \
                      else status.tuple_action(tup, stup[0])
            inst = [parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                     actions)]
            ## Apply an egress meter for the destination that this
            ## packet has come from.
            if outmtr is not None:
                inst.insert(0, parser.OFPInstructionMeter(outmtr))
            mymsg = parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                      cookie=group,
                                      datapath=dp,
                                      table_id=2,
                                      priority=2,
                                      match=match,
                                      instructions=inst)
            dp.send_msg(mymsg)

        ## Make sure that, by deleting any existing MAC-specific rule,
        ## if the source address is seen again on a different port in
        ## the slice, the controller will deal with it.  Use the
        ## tuple's group to ensure we only delete rules for this
        ## slice.
        for stup in tups:
            if stup == tup:
                continue
            sgroup = status.get_group_for_tuple(stup)
            (_, tbl, _) = status.tuple_match(stup, mac)
            match = parser.OFPMatch(eth_src=mac)
            mymsg = parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                      cookie=sgroup,
                                      cookie_mask=0xffffffffffffffff,
                                      datapath=dp,
                                      table_id=tbl,
                                      buffer_id=ofp.OFPCML_NO_BUFFER,
                                      out_port=ofp.OFPP_ANY,
                                      out_group=ofp.OFPG_ANY,
                                      match=match)
            dp.send_msg(mymsg)

        ## In the source table, prevent traffic from this source
        ## address on this port from being forwarded to the controller
        ## again.  Label the rule with the tuple's group, so we can
        ## distinguish it from rules for the same MAC in other slices.
        (match, tbl, prio) = status.tuple_match(tup, mac)
        actions = [parser.OFPActionSetField(metadata=group)]
        if len(tup) > 2:
            actions.append(parser.OFPActionPopVlan())
        inst = [parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                             actions),
                parser.OFPInstructionGotoTable(2)]
        ## Apply an ingress meter.
        if inmtr is not None:
            inst.insert(0, parser.OFPInstructionMeter(inmtr))
        mymsg = parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                  cookie=group,
                                  datapath=dp,
                                  table_id=tbl,
                                  priority=prio,
                                  idle_timeout=timeout,
                                  flags=ofp.OFPFF_SEND_FLOW_REM,
                                  match=match,
                                  instructions=inst)
        dp.send_msg(mymsg)

        return slize

    @set_ev_cls(ofp_event.EventOFPPacketIn, MAIN_DISPATCHER)
    def packet_in_handler(self, ev):
        msg = ev.msg
        tbl = msg.table_id
        dp = msg.datapath
        ofp = dp.ofproto
        parser = dp.ofproto_parser
        status = self.switches[dp.id]

        ## We are only called if a packet has an unrecognized source
        ## address.  Extract the fields we're interested in.
        pkt = packet.Packet(msg.data)
        eth = pkt.get_protocol(ethernet.ethernet)
        mac = eth.src
        dmac = eth.dst
        in_port = msg.match['in_port']

        ## Form the tuple from in_port, metadata and vlan_vid.  Pop
        ## the VLAN tag if present.
        pop_vlan = False
        if tbl == 0:
            tup = (in_port,)
        elif 'vlan_vid' in msg.match:
            tup = (in_port,
                   msg.match['metadata'],
                   msg.match['vlan_vid'] & 0x0fff)
            pop_vlan = True
        else:
            tup = (in_port,
                   msg.match['metadata'])

        ## Learn that this MAC address has most recently been seen on
        ## this port.
        slize = self._learn(dp, tup, mac)

        ## Commit our changes before we submit the packet back through
        ## the tables.
        #dp.send_msg(parser.OFPBarrierRequest(dp))

        ## Where is this packet going?
        dtup = slize.lookup(dmac)

        ## Build up the steps to deal with the packet.
        if dtup is None:
            ## If we haven't seen this destination recently, we output
            ## to the source tuple's group action.
            group = status.get_group_for_tuple(tup)
            if group is None:
                return
            actions = [parser.OFPActionGroup(group)]
        elif dtup == tup:
            ## Don't loop packets back.
            return
        else:
            ## Perform the defined actions for the destination tuple.
            actions = status.tuple_action(dtup, in_port)
            ## Apply an egress meter.
            if hasattr(parser, 'OFPActionMeter'):
                outmtr = status.get_outmeter(dtup)
                if outmtr is not None:
                    actions.insert(0, parser.OFPActionMeter(outmtr))

        ## Make sure we've popped off the extra VLAN before we do
        ## anything else.
        if pop_vlan:
            actions.insert(0, parser.OFPActionPopVlan())

        ## Issue the packet.
        mymsg = parser.OFPPacketOut(datapath=dp,
                                    buffer_id=msg.buffer_id,
                                    in_port=in_port,
                                    actions=actions)
        dp.send_msg(mymsg)
        
class SliceController(ControllerBase):
    def __init__(self, req, link, data, **config):
        super(SliceController, self).__init__(req, link, data, **config)
        self.ctrl= data[tuple_slicer_instance_name]

    @route('slicer', url, methods=['GET'],
           requirements={ 'dpid': dpid_lib.DPID_PATTERN })
    def get_config(self, req, **kwargs):
        dpid = dpid_lib.str_to_dpid(kwargs['dpid'])
        if dpid not in self.ctrl.switches:
            return Response(status=404)

        status = self.ctrl.switches[dpid]
        body = json.dumps(status.get_config()) + "\n"
        return Response(content_type='application/json', body=body)

    @route('slicer', url, methods=['POST'],
           requirements={ 'dpid': dpid_lib.DPID_PATTERN })
    def set_config(self, req, **kwargs):
        dpid = dpid_lib.str_to_dpid(kwargs['dpid'])

        try:
            new_config = req.json if req.body else {}
        except ValueError:
            return Response(status=400)

        if dpid not in self.ctrl.switches:
            self.ctrl.switches[dpid] = SwitchStatus()
        status = self.ctrl.switches[dpid]
        if 'disused' in new_config:
            for tup in new_config['disused']:
                status.discard_tuple(tup)
        if 'slices' in new_config:
            for lps in new_config['slices']:
                ps = set()
                inrates = {}
                outrates = {}
                for mp in lps:
                    tup = tuple(mp['circuit'])
                    ps.add(tup)
                    if 'ingress-bw' in mp:
                        inrates[tup] = mp['ingress-bw']
                    if 'egress-bw' in mp:
                        outrates[tup] = mp['egress-bw']
                LOG.info("%016x: creating %s", dpid, tuples_text(ps))
                status.create_slice(ps, inrates, outrates)
        status.revalidate()
        LOG.info("%016x: completed changes", dpid)
        if 'learn' in new_config:
            dp = api.get_datapath(self.ctrl, dpid)
            mac = new_config['learn']['mac']
            tup = tuple(new_config['learn']['tuple'])
            timeout = new_config['learn']['timeout'] \
                      if 'timeout' in new_config['learn'] \
                         else 600
            self.ctrl._learn(dp, tup, mac, timeout=timeout);

        body = json.dumps(status.get_config()) + "\n"
        return Response(content_type='application/json', body=body)
