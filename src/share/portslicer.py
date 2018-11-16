## Copyright 2018, Regents of the University of Lancaster
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

## Port-sliced learning switch controller

## A single switch is sliced to behave like several, each with a
## discrete set of ofports. When a slice has two ports, E-Line
## behaviour is applied.  Otherwise, a straight-forward learning
## switch is implemented.  Two regular OpenFlow tables and one group
## table are used.

## T0's highest-priority rules implement E-Line behaviour.  The next
## match <in_port, eth_src> tuples that have been seen, and redirect
## to T1.  The default rule sends to the controller, and only applies
## to traffic of multiport slices.

## T1's highest-priority rules match <in_port, eth_dst> tuples, and
## send to specific ports.  The next match <in_port>, and send to a
## group assigned to the slice.  (To save rules, we could get T0 to
## set metadata to the slice's group, then match on the metadata in
## T1.  However, the Corsa doesn't support metadata, so we hack it by
## getting T0 to push the group as a VLAN tag, and then popping it in
## the actions of T1.  Is it worth it?  A global flag enables this
## meta-as-VLAN hack.)

## Each multiport slice gets a group, whose behaviour is to send to
## all ports in the slice (implicitly excepting the in_port).
## Two-port slices don't need these, as broadcast is the same as
## unicast.

## The controller implements a REST interface allowing any number of
## port sets to be specified.  The controller retains all port set
## information, and if any new sets intersect with old ones, rules for
## all old ones are revised.

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

port_slicer_instance_name = 'slicer_api_app'
url = '/slicer/api/v1/config/{dpid}'

use_vlans_as_meta=False

class Slice:
    def __init__(self, outer):
        self.switch = outer

        ## The set of ports that should be part of this slice
        self.target = set()

        ## The set of ports that have the necessary rules to be
        ## part of this slice
        self.established = set()

        ## The OF group allocated to this slice, or negative if
        ## not allocated
        self.group = -1

        ## Keep track of the port on which a MAC was last seen.
        self.mac_port = { }

    def see(self, mac, port):
        self.mac_port[mac] = port

    def unsee(self, mac, port):
        if port in self.target:
            self.mac_port.pop(mac, None)

    def lookup(self, mac):
        return self.mac_port.get(mac)

    def get_group(self):
        return self.group

    def get_ports(self):
        return frozenset(self.target)

    def sanitize(self):
        ## Reduce the target set of ports to those that actually
        ## exist, while retaining the intended target.
        self.sanitized = self.target & self.switch.known_ports

    def match(self):
        self.established = set(self.sanitized)

    def invalidate(self):
        ## Make it look as if we had no ports, and then had several
        ## suddenly added, and that this new set is what we've got
        ## now.
        self.established.clear()
        self.group = -1
        self.switch.invalid_slices.add(self)
        

    ## Ensure that this slice has the right set of static rules, based
    ## on its port set.  If the number of ports is greater than two, a
    ## group entry is needed, outputting to all ports in the set.  If
    ## there are exactly two ports, two E-Line rules must exist,
    ## exchanging traffic between them.  This function only deletes
    ## rules, and releases groups.  Use add_static_rules to create
    ## rules and allocate groups.
    def delete_static_rules(self):
        if self.sanitized == self.established:
            ## Nothing has actually changed.
            return

        dp = self.switch.datapath
        ofp = dp.ofproto
        ofp_parser = dp.ofproto_parser
        LOG.info("%016x: %s -> %s", dp.id,
                 list(self.established), list(self.sanitized))

        ## Work out which ports are now invalid.
        if len(self.established) == 2:
            ## We're either changing from one E-Line to another (so we
            ## have to replace the old port-exchanging pair with an
            ## alternative), or from E-Line to one or zero ports (so
            ## we have to delete the old port-exchanging pair
            ## outright), or from E-Line to learning switch (so we
            ## have to replace the port exchangers with learned rules
            ## plus defaults to send to the controller).  In any case,
            ## we have to delete rules for all old ports in the slice.
            oldports = self.established
        elif len(self.sanitized) <= 2:
            ## We're either changing from learning switch to E-Line
            ## (so learned and controller rules for each port have to
            ## be replaced with a port-exchanging pair), or from a
            ## learning switch to one or zero ports (so learned and
            ## controller rules for each port have to be deleted), or
            ## from one or zero ports to one or zero ports, so there
            ## won't really be anything to delete.
            oldports = self.established
        else:
            ## We've changed the set of ports on a learning switch,
            ## while still remaining a learning switch.  Delete any
            ## ports that have been lost.
            oldports = self.established - self.sanitized
        for p in oldports:
            LOG.info("%016x: deleting rules for port %d", dp.id, p)
            match = ofp_parser.OFPMatch(in_port=p)
            msg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                        datapath=dp,
                                        table_id=0,
                                        match=match,
                                        buffer_id=ofp.OFPCML_NO_BUFFER,
                                        out_port=ofp.OFPP_ANY,
                                        out_group=ofp.OFPG_ANY)
            dp.send_msg(msg)
            match = ofp_parser.OFPMatch()
            msg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                        datapath=dp,
                                        table_id=1,
                                        match=match,
                                        buffer_id=ofp.OFPCML_NO_BUFFER,
                                        out_port=p,
                                        out_group=ofp.OFPG_ANY)
            dp.send_msg(msg)

        if len(self.sanitized) <= 2 and len(self.established) > 2:
            ## We're changing from a multi-port learning switch to
            ## something simpler.

            assert self.group >= 0
            ## We should have a group, but don't need one any more.
            ## Release it.
            LOG.info("%016x: releasing group %d", dp.id, self.group)

            ## Remove the group definition from the switch table,
            ## automatically deleting the destination rule that
            ## directs to it.
            msg = ofp_parser.OFPGroupMod(datapath=dp,
                                         command=ofp.OFPGC_DELETE,
                                         group_id=self.group)
            dp.send_msg(msg)

            ## Release the group number, and forget it.
            self.switch.release_group(self.group)
            self.group = -1

        return

    ## Ensure that this slice has the right set of static rules, based
    ## on its port set.  If the number of ports is greater than two, a
    ## group entry is needed, outputting to all ports in the set.  If
    ## there are exactly two ports, two E-Line rules must exist,
    ## exchanging traffic between them.  This function only adds
    ## rules, and allocates groups.  Use delete_static_rules to delete
    ## rules and release groups.
    def add_static_rules(self):
        if self.sanitized == self.established:
            ## Nothing has actually changed.
            return

        dp = self.switch.datapath
        ofp = dp.ofproto
        ofp_parser = dp.ofproto_parser

        ## A slice with fewer than 2 ports should have no OpenFlow
        ## manifestations.  The default drop rule should apply.
        if len(self.sanitized) < 2:
            return

        if len(self.sanitized) == 2:
            ## We have two ports now, and we either didn't have
            ## exactly two before, or at least one has changed (and
            ## we've already deleted the old rules).  Create E-Line
            ## rules.
            pl = list(self.sanitized)
            for i in [ 0, 1 ]:
                LOG.info("%016x: adding e-line for %d->%d", dp.id,
                         pl[i], pl[1-i])
                match = ofp_parser.OFPMatch(in_port=pl[i])
                actions = [ofp_parser.OFPActionOutput(pl[1-i])]
                inst = [ofp_parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                         actions)]
                msg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                            datapath=dp,
                                            table_id=0,
                                            priority=3,
                                            match=match,
                                            instructions=inst)
                dp.send_msg(msg)
            return

        ## In a learning switch, ensure that low-priority rules
        ## for each port send to the controller.
        if len(self.established) <= 2:
            newports = self.sanitized
        else:
            newports = self.sanitized - self.established
        for p in newports:
            LOG.info("%016x: adding ctrl rule for %d", dp.id, p)
            match = ofp_parser.OFPMatch(in_port=p)
            actions = [ofp_parser.OFPActionOutput(ofp.OFPP_CONTROLLER,
                                                  65535)]
            inst = [ofp_parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                     actions)]
            msg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                        datapath=dp,
                                        table_id=0,
                                        priority=1,
                                        match=match,
                                        instructions=inst)
            dp.send_msg(msg)

        if len(self.established) <= 2:
            assert self.group < 0
            ## We need a group now, but didn't before.  Allocate one.
            self.group = self.switch.claim_group()
            LOG.info("%016x: creating group %d %s", dp.id,
                     self.group, list(self.sanitized))

            ## Create the group entry in the switch with all the ports
            ## in the target set.
            buckets = []
            for p in self.sanitized:
                output = ofp_parser.OFPActionOutput(p)
                buckets.append(ofp_parser.OFPBucket(actions=[output]))
            msg = ofp_parser.OFPGroupMod(datapath=dp,
                                         command=ofp.OFPGC_ADD,
                                         type_=ofp.OFPGT_ALL,
                                         group_id=self.group,
                                         buckets=buckets)
            dp.send_msg(msg)

            if use_vlans_as_meta:
                ## Make sure that unknown destinations in this slice
                ## are broadcast to the group.  We match the slice by
                ## the pseudo-VLAN tag we added in T0, and this must
                ## be removed first.  This rule will automatically be
                ## deleted when the group is deleted.
                match = ofp_parser.OFPMatch(vlan_vid=(self.group|0x1000))
                actions = [ofp_parser.OFPActionPopVlan(),
                           ofp_parser.OFPActionGroup(self.group)]
                inst = [ofp_parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                         actions)]
                msg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                            datapath=dp,
                                            table_id=1,
                                            priority=1,
                                            match=match,
                                            instructions=inst)
                dp.send_msg(msg)

        if not use_vlans_as_meta:
            ## Make any packet from one of our ports but with an
            ## unrecognized destination go out on all ports (except
            ## in_port).
            for p in newports:
                LOG.info("%016x: unknown macs from %d to controller", dp.id, p)
                match = ofp_parser.OFPMatch(in_port=p)
                actions = [ofp_parser.OFPActionGroup(self.group)]
                inst = [ofp_parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                         actions)]
                msg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                            datapath=dp,
                                            table_id=1,
                                            priority=1,
                                            match=match,
                                            instructions=inst)
                dp.send_msg(msg)

        ## The set has changed.  Set the group to output to the target
        ## set.
        LOG.info("%016x: updating group %d %s", dp.id,
                 self.group, list(self.sanitized))
        buckets = []
        for p in self.sanitized:
            output = ofp_parser.OFPActionOutput(p)
            buckets.append(ofp_parser.OFPBucket(actions=[output]))
        msg = ofp_parser.OFPGroupMod(datapath=dp,
                                     command=ofp.OFPGC_MODIFY,
                                     group_id=self.group,
                                     buckets=buckets)
        dp.send_msg(msg)
        return

    ## Ensure that a port belongs to this slice.  If it belongs to
    ## something else, get that to abandon it.
    def adopt(self, port):
        if port in self.target:
            return
        self.target.add(port)
        if port in self.switch.target_index:
            other = self.switch.target_index[port]
            assert other != self
            self.switch.target_index[port].abandon(port)
        self.switch.target_index[port] = self
        self.switch.invalid_slices.add(self)

    def abandon(self, port):
        if port in self.target:
            self.target.discard(port)
            us = self.switch.target_index.pop(port)
            assert us == self
            self.switch.invalid_slices.add(self)
        return

class SwitchStatus:
    def __init__(self):
        self.datapath = None

        self.known_ports = set()

        ## These groups and anything after the largest value are free.
        self.free_groups = set([ 0 ])

        ## port -> Slice
        self.target_index = { }

        ## Keep track of which slices have been modified since last
        ## revalidation.
        self.invalid_slices = set()

    def set_datapath(self, dp):
        self.datapath = dp
        self.known_ports = set()

    def get_config(self):
        result = []
        for slize in set(self.target_index.values()):
            result.append(list(slize.get_ports()))
        return result

    def get_slice(self, port):
        return self.target_index.get(port)

    def create_slice(self, ports):
        ports = frozenset(ports)
        if len(ports) == 0:
            return None

        ## Find a slice with the maximum overlap.
        best_slize = None
        best_overlap = 0
        for p in ports:
            slize = self.target_index.get(p)
            if slize is None:
                continue
            overlap = len(slize.get_ports() & ports)
            if overlap > best_overlap:
                best_overlap = overlap
                best_slize = slize

        if best_slize is not None:
            ## There is a suitable slice.  Make it adopt any new ports
            ## it needs.
            for p in ports - best_slize.get_ports():
                best_slize.adopt(p)
            ## Create a new slice to retain the abandoned ports.
            abandoned = best_slize.get_ports() - ports
            other_slize = Slice(self)
            for p in abandoned:
                other_slize.adopt(p)
            return best_slize

        ## There's no overlap, so create a new slice.  Steal its ports
        ## from other slices.
        slize = Slice(self)
        for p in ports:
            slize.adopt(p)
        return slize

    ## Call only from a slice.
    def claim_group(self):
        ## Get the lowest free group.
        result = min(self.free_groups)
        self.free_groups.discard(result)

        ## If we appear to have run out, just add back in one more
        ## than what we took.
        if len(self.free_groups) == 0:
            self.free_groups.add(result + 1)

        return result

    ## Call only from a slice.
    def release_group(self, group):
        self.free_groups.add(group)

    def port_added(self, port):
        if port > 0x7fffffff:
            return
        dp = self.datapath
        LOG.info("%016x: gained port %d", dp.id, port)
        self.known_ports.add(port)

        ## Invalidate any slice targeted on that port.
        slize = self.target_index.get(port)
        if slize is not None:
            self.invalid_slices.add(slize)
        return

    def port_removed(self, port):
        dp = self.datapath
        LOG.info("%016x: lost port %d", dp.id, port)
        self.known_ports.discard(port)

        ## Invalidate any slice targeted on that port.
        slize = self.target_index.get(port)
        if slize is not None:
            self.invalid_slices.add(slize)
        return

    def discard_port(self, port):
        slize = self.target_index.get(port)
        if slize is not None:
            slize.abandon(port)
        return

    def invalidate(self):
        for slize in set(self.target_index.values()):
            slize.invalidate()

    def reset_port(self, port):
        if port not in self.known_ports:
            return
        dp = self.datapath
        ofp = dp.ofproto
        ofp_parser = dp.ofproto_parser

        ## Delete dynamic rules in the source table matching this
        ## in_port and passing on to the destination table.
        LOG.info("%016x: resetting port %d in T0", dp.id, port)
        match = ofp_parser.OFPMatch(in_port=port)
        msg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                    datapath=dp,
                                    table_id=0,
                                    match=match,
                                    buffer_id=ofp.OFPCML_NO_BUFFER,
                                    out_port=ofp.OFPP_ANY,
                                    out_group=ofp.OFPG_ANY)
        dp.send_msg(msg)

        ## Delete dynamic rules in the destination table outputting
        ## unicast to the port.
        LOG.info("%016x: resetting port %d in T1", dp.id, port)
        match = ofp_parser.OFPMatch()
        msg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                    datapath=dp,
                                    table_id=1,
                                    match=match,
                                    buffer_id=ofp.OFPCML_NO_BUFFER,
                                    out_port=port,
                                    out_group=ofp.OFPG_ANY)
        dp.send_msg(msg)

    def revalidate(self):
        dp = self.datapath
        if dp is None:
            LOG.info("no datapath!")
            return
        LOG.info("%016x: revalidating...", dp.id)

        ## Identify all ports that have been removed from their
        ## slices, and remove rules pertaining to them.
        ports_to_reset = set()
        for inval in self.invalid_slices:
            inval_missing = inval.established.difference(inval.target)
            ports_to_reset.update(inval_missing)
        for port in ports_to_reset:
            self.reset_port(port)

        ## Work out the subset of target ports that actually exist.
        for slize in self.invalid_slices:
            slize.sanitize()

        ## Ensure that each modified slice has the right static rules
        ## according to its target set.  This is done in two passes,
        ## one to delete rules and groups, and one to add them.
        for slize in self.invalid_slices:
            slize.delete_static_rules()
        for slize in self.invalid_slices:
            slize.add_static_rules()

        ## Make all established port sets match the targets.
        for slize in self.invalid_slices:
            slize.match()
        self.invalid_slices.clear()

        ## Clean out slices with only one port.  These should have no
        ## remaining rules, so no OpenFlow operations are required.
        for slize in set(self.target_index.values()):
            ports = slize.get_ports()
            if len(ports) <= 1:
                del self.target_index[list(ports)[0]]

        LOG.info("%016x: revalidating complete", dp.id)
        return
            

class PortSlicer(app_manager.RyuApp):
    _CONTEXTS = {'wsgi': WSGIApplication}
    OFP_VERSIONS = [ofproto_v1_3.OFP_VERSION]

    def __init__(self, *args, **kwargs):
        super(PortSlicer, self).__init__(*args, **kwargs)

        ## DPID -> SwitchStatus
        self.switches = { }

        wsgi = kwargs['wsgi']
        wsgi.register(SliceController,
                      { port_slicer_instance_name: self })

    def _configure_set(self, dp, ports):
        ofp = dp.ofproto
        ofp_parser = dp.ofproto_parser

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
        LOG.info("%016x: port %d removal complete", dp.id, port.port_no)
        return

    @set_ev_cls(dpset.EventDP, dpset.DPSET_EV_DISPATCHER)
    def datapath_handler(self, ev):
        dp = ev.dp
        ofp = dp.ofproto
        ofp_parser = dp.ofproto_parser

        if not ev.enter:
            ## A switch has been detached.
            if dp.id not in self.switches:
                return
            status = self.switches[dp.id]
            status.set_datapath(None)
            return

        ## A switch has been attached.  Set up static flows.
        LOG.info("%016x: New switch", dp.id)

        ## Delete all flows in the source, destination and group
        ## tables.
        match = ofp_parser.OFPMatch()
        mymsg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                      datapath=dp,
                                      table_id=0,
                                      buffer_id=ofp.OFPCML_NO_BUFFER,
                                      out_port=ofp.OFPP_ANY,
                                      out_group=ofp.OFPG_ANY,
                                      match=match)
        dp.send_msg(mymsg)
        mymsg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                      datapath=dp,
                                      table_id=1,
                                      buffer_id=ofp.OFPCML_NO_BUFFER,
                                      out_port=ofp.OFPP_ANY,
                                      out_group=ofp.OFPG_ANY,
                                      match=match)
        dp.send_msg(mymsg)

        ## Delete all groups.
        mymsg = ofp_parser.OFPGroupMod(datapath=dp,
                                       command=ofp.OFPGC_DELETE,
                                       group_id=ofp.OFPG_ALL)
        dp.send_msg(mymsg)

        ## Drop LLDP packets.
        match = ofp_parser.OFPMatch(vlan_vid=0x0000, eth_type=0x88CC)
        actions = []
        inst = [ofp_parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                 actions)]
        mymsg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                      datapath=dp,
                                      table_id=0,
                                      priority=4,
                                      match=match,
                                      instructions=inst)
        dp.send_msg(mymsg)

        ## The default rule should just drop everything.  Earlier
        ## rules should match specific ports, and send to the
        ## controller, but only for multi-port slices.  (This command
        ## has been disabled, since the default behaviour of a table
        ## is to drop anyway, so the rule is redundant.)
        # match = ofp_parser.OFPMatch()
        # inst = [ofp_parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS, [])]
        # mymsg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_ADD,
        #                               datapath=dp,
        #                               table_id=0,
        #                               priority=0,
        #                               match=match,
        #                               instructions=inst)
        # dp.send_msg(mymsg)

        ## Mark all slices as invalid, then revalidate them.
        if dp.id not in self.switches:
            self.switches[dp.id] = SwitchStatus()
        status = self.switches[dp.id]
        status.set_datapath(dp)
        for p in ev.ports:
            status.port_added(p.port_no)
        status.invalidate()
        #status.create_slice([ 1, 2, 3 ]) ## Test
        status.revalidate()
        #self._learn(dp, 2, "54:e1:ad:4a:29:40", timeout=15) ## Test

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
            ## We've not seen a packet from this MAC on its last port
            ## for a while.
            self._not_heard_from(dp, match['in_port'], match['eth_src'])

    def _not_heard_from(self, dp, in_port, mac):
        ofp = dp.ofproto
        ofp_parser = dp.ofproto_parser
        status = self.switches[dp.id]
        slize = status.get_slice(in_port)
        group = slize.get_group()

        LOG.info("%016x: P%03d/G%03d/%17s not heard from",
                 dp.id, in_port, group, mac)

        slize.unsee(mac, in_port)

        ## Delete learned rules in the destination table matching this
        ## destination and belonging to this group (as identified by
        ## cookie).
        match = ofp_parser.OFPMatch(eth_dst=mac)
        msg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                    cookie=group,
                                    cookie_mask=0xffffffffffffffff,
                                    datapath=dp,
                                    table_id=1,
                                    match=match,
                                    buffer_id=ofp.OFPCML_NO_BUFFER,
                                    out_port=ofp.OFPP_ANY,
                                    out_group=ofp.OFPG_ANY)
        dp.send_msg(msg)

    def _learn(self, dp, port, mac, timeout=600):
        LOG.info("%016x: %17s new on port %d",
                 dp.id, mac, port)
        status = self.switches[dp.id]
        status.revalidate()

        ## Is this port allocated to a slice?
        slize = status.get_slice(port)
        if slize is None:
            LOG.info("%016x: %17s: port %d not in slice",
                     dp.id, mac, port)
            return

        ## We should only receive packets on ports belonging to
        ## multiport slices, as E-Line rules would have already dealt
        ## with the packet.  Every multiport slice should have a group
        ## id allocatde to it.  If not, this learning command is an
        ## aberration.  (Perhaps it came through the REST interface.)
        group = slize.get_group()
        if group < 0:
            LOG.info("%016x: %17s: port %d not in learning switch",
                     dp.id, mac, port)
            return

        ## Get the set of ports for this slice.  It should have at
        ## least 3, including our own.
        ports = slize.get_ports()
        if port not in ports:
            LOG.info("%016x: %17s: port %d not in this learning switch (%s)",
                     dp.id, mac, port, ports)
            return

        assert len(ports) > 2
        assert port in ports

        ## Record the MAC->port mapping, so we can output packets
        ## ourselves.
        slize.see(mac, port)

        ofp = dp.ofproto
        ofp_parser = dp.ofproto_parser

        if use_vlans_as_meta:
            ## In the destination table, map the destination address and
            ## slice id (encoded as VLAN id) to the destination's port,
            ## overwriting any previous mapping.  This prevents further
            ## flooding for that destination on that slice.  Make sure the
            ## fake VLAN tag used to carry the slice's group id is popped
            ## before transmission.
            match = ofp_parser.OFPMatch(vlan_vid=(group|0x1000), eth_dst=mac)
            actions = [ofp_parser.OFPActionPopVlan(),
                       ofp_parser.OFPActionOutput(port)]
            inst = [ofp_parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                     actions)]
            mymsg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                          cookie=group,
                                          datapath=dp,
                                          table_id=1,
                                          priority=2,
                                          match=match,
                                          instructions=inst)
            dp.send_msg(mymsg)
        else:
            ## For each port, match the destination and send to the
            ## port associated with the mac.  However, for traffic
            ## matching that destination but coming from the
            ## associated port, simply drop it.  Set the cookie to
            ## record which group these rules belong to, so we can
            ## delete them without deleting similar rules for the same
            ## mac in a different group.
            for p in ports:
                match = ofp_parser.OFPMatch(in_port=p, eth_dst=mac)
                if p == port:
                    LOG.info("%016x: adding %d/%17s -> drop", dp.id, p, mac)
                    actions = []
                else:
                    LOG.info("%016x: adding %d/%17s -> %d", dp.id, p, mac, port)
                    actions = [ofp_parser.OFPActionOutput(port)]
                inst = [ofp_parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                         actions)]
                mymsg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                              cookie=group,
                                              datapath=dp,
                                              table_id=1,
                                              priority=2,
                                              match=match,
                                              instructions=inst)
                dp.send_msg(mymsg)

        ## Make sure that, if the source address is seen again on a
        ## different port in the slice, the controller will deal with
        ## it.
        for p in ports:
            if p == port:
                continue
            match = ofp_parser.OFPMatch(in_port=p, eth_src=mac)
            mymsg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_DELETE,
                                          datapath=dp,
                                          table_id=0,
                                          buffer_id=ofp.OFPCML_NO_BUFFER,
                                          out_port=ofp.OFPP_ANY,
                                          out_group=ofp.OFPG_ANY,
                                          match=match)
            dp.send_msg(mymsg)

        ## In the source table, prevent traffic from this source
        ## address on this port from being forwarded to the controller
        ## again.
        match = ofp_parser.OFPMatch(in_port=port, eth_src=mac)
        if use_vlans_as_meta:
            ## We record which slice the packet belongs to by pushing
            ## a VLAN tag with its allocated group id.  The
            ## destination table will pop it off before transmission,
            ## so it never leaves the switch.  TODO: Drop setgrp, and
            ## just set vlan_vid=0x1000|group?
            setgrp = ofp_parser.OFPMatchField.make(dp.ofproto.OXM_OF_VLAN_VID,
                                                   0x1000|group)
            actions = [ofp_parser.OFPActionPushVlan(ether.ETH_TYPE_8021Q),
                       ofp_parser.OFPActionSetField(setgrp)]
        else:
            actions = []
        inst = [ofp_parser.OFPInstructionActions(ofp.OFPIT_APPLY_ACTIONS,
                                                 actions),
                ofp_parser.OFPInstructionGotoTable(1)]
        mymsg = ofp_parser.OFPFlowMod(command=ofp.OFPFC_ADD,
                                      datapath=dp,
                                      table_id=0,
                                      priority=2,
                                      idle_timeout=timeout,
                                      flags=ofp.OFPFF_SEND_FLOW_REM,
                                      match=match,
                                      instructions=inst)
        dp.send_msg(mymsg)


    @set_ev_cls(ofp_event.EventOFPPacketIn, MAIN_DISPATCHER)
    def packet_in_handler(self, ev):
        msg = ev.msg
        dp = msg.datapath
        ofp = dp.ofproto
        ofp_parser = dp.ofproto_parser

        ## We are only called if a packet has an unrecognized source
        ## address.  Extract the fields we're interested in.
        pkt = packet.Packet(msg.data)
        eth = pkt.get_protocol(ethernet.ethernet)
        mac = eth.src
        in_port = msg.match['in_port']

        ## Learn that this MAC address has most recently been seen on
        ## this port.
        self._learn(dp, in_port, mac)

        ## Which slice does this belong to?  Get its port set, and
        ## push out to each port except the input.  If we know the
        ## destination MAC maps to a particular port, limit it to that
        ## port.
        status = self.switches[dp.id]
        slize = status.get_slice(in_port)
        if slize is None:
            return
        outs = slize.get_ports()
        out1 = slize.lookup(eth.dst)
        actions = []
        if out1 is not None and out1 in outs:
            actions.append(ofp_parser.OFPActionOutput(out1))
        else:
            for p in outs:
                if p == in_port:
                    continue
                actions.append(ofp_parser.OFPActionOutput(p))
        mymsg = ofp_parser.OFPPacketOut(datapath=dp,
                                        buffer_id=msg.buffer_id,
                                        in_port=in_port,
                                        actions=actions)
        dp.send_msg(mymsg)
        return
        
class SliceController(ControllerBase):
    def __init__(self, req, link, data, **config):
        super(SliceController, self).__init__(req, link, data, **config)
        self.ctrl = data[port_slicer_instance_name]

    @route('slicer', url, methods=['GET'],
           requirements={ 'dpid': dpid_lib.DPID_PATTERN })
    def get_config(self, req, **kwargs):
        dpid = dpid_lib.str_to_dpid(kwargs['dpid'])
        if dpid not in self.ctrl.switches:
            self.ctrl.switches[dpid] = SwitchStatus()
            #return Response(status=404)
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
            raise Response(status=400)

        if dpid not in self.ctrl.switches:
            self.ctrl.switches[dpid] = SwitchStatus()
        LOG.info("%016x: post %s", dpid, new_config)
        status = self.ctrl.switches[dpid]
        if 'disused' in new_config:
            for p in new_config['disused']:
                LOG.info("%016x: dropping %d", dpid, p)
                status.discard_port(p)
        if 'slices' in new_config:
            for ps in new_config['slices']:
                ps = set(ps)
                LOG.info("%016x: creating %s", dpid, list(ps))
                status.create_slice(ps)
        status.revalidate()
        LOG.info("%016x: completed changes", dpid)
        if 'learn' in new_config:
            dp = api.get_datapath(self.ctrl, dpid)
            mac = new_config['learn']['mac'];
            port = new_config['learn']['port'];
            timeout = new_config['learn']['timeout'] \
                      if 'timeout' in new_config['learn'] \
                         else 600
            self.ctrl._learn(dp, port, mac, timeout=timeout);

        body = json.dumps(status.get_config()) + "\n"
        return Response(content_type='application/json', body=body)
