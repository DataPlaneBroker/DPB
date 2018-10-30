all::

FIND=find
SED=sed
XARGS=xargs
PRINTF=printf

-include dataplanebroker-env.mk

jars += $(SELECTED_JARS)
jars += $(TEST_JARS)

SELECTED_JARS += initiate-dpb-core
trees_initiate-dpb-core += core
deps_core += util
roots_core += uk.ac.lancs.routing.span.Graphs
roots_core += uk.ac.lancs.routing.span.SpanningTreeComputer
roots_core += uk.ac.lancs.routing.span.DistanceVectorComputer
roots_core += uk.ac.lancs.networks.NetworkControl
roots_core += uk.ac.lancs.networks.mgmt.Aggregator
roots_core += uk.ac.lancs.networks.mgmt.Switch
roots_core += uk.ac.lancs.networks.mgmt.TerminalManagementException
roots_core += uk.ac.lancs.networks.mgmt.UnknownTerminalException
roots_core += uk.ac.lancs.networks.mgmt.TerminalExistsException
roots_core += uk.ac.lancs.networks.mgmt.TrunkManagementException
roots_core += uk.ac.lancs.networks.mgmt.OwnTerminalException
roots_core += uk.ac.lancs.networks.mgmt.LabelsInUseException
roots_core += uk.ac.lancs.networks.mgmt.UnknownTrunkException
roots_core += uk.ac.lancs.networks.jsoncmd.JsonSwitch
roots_core += uk.ac.lancs.networks.jsoncmd.JsonAggregator
roots_core += uk.ac.lancs.networks.jsoncmd.JsonSwitchServer
roots_core += uk.ac.lancs.networks.jsoncmd.JsonAggregatorServer
roots_core += uk.ac.lancs.networks.jsoncmd.SSHNetworkAgentFactory
roots_core += uk.ac.lancs.networks.transients.DummySwitch
roots_core += uk.ac.lancs.networks.transients.TransientAggregator
roots_core += uk.ac.lancs.networks.persist.PersistentAggregatorAgentFactory
roots_core += uk.ac.lancs.networks.persist.PersistentSwitchAgentFactory
roots_core += uk.ac.lancs.networks.fabric.DummyFabricAgentFactory
roots_core += uk.ac.lancs.networks.apps.Commander
roots_core += uk.ac.lancs.networks.apps.NetworkServer

SELECTED_JARS += initiate-dpb-corsa
trees_initiate-dpb-corsa += corsa
deps_corsa += util
deps_corsa += core
roots_corsa += uk.ac.lancs.networks.corsa.DP2000FabricAgentFactory
roots_corsa += uk.ac.lancs.networks.corsa.rest.CorsaREST

SELECTED_JARS += initiate-dpb-openflow
trees_initiate-dpb-openflow += openflow
deps_openflow += util
deps_openflow += core
roots_openflow += \
uk.ac.lancs.networks.openflow.VLANCircuitFabricAgentFactory

SELECTED_JARS += initiate-dpb-util
trees_initiate-dpb-util += util
roots_util += uk.ac.lancs.config.ConfigurationContext
roots_util += uk.ac.lancs.agent.AgentFactory
roots_util += uk.ac.lancs.agent.CacheAgent
roots_util += uk.ac.lancs.rest.RESTClient
roots_util += uk.ac.lancs.rest.SecureSingleCertificateHttpProvider

TEST_JARS += tests
roots_tests += TestOddSpan
roots_tests += TestInitSpan
roots_tests += TestDummy
roots_tests += TestDV
roots_tests += TestDummyInitiateTopology
roots_tests += TestGeographicSpan
deps_tests += core

JARDEPS_OUTDIR=out
JARDEPS_SRCDIR=src/java-tree
JARDEPS_MERGEDIR=src/java-merge

include jardeps.mk
-include jardeps-install.mk


scripts += dpb-server
scripts += dbp-client
scripts += dbp-ssh-agent


include binodeps.mk


DOC_PKGS += uk.ac.lancs.routing.metric
DOC_PKGS += uk.ac.lancs.routing.span
DOC_PKGS += uk.ac.lancs.networks
DOC_PKGS += uk.ac.lancs.networks.apps
DOC_PKGS += uk.ac.lancs.networks.jsoncmd
DOC_PKGS += uk.ac.lancs.networks.mgmt
DOC_PKGS += uk.ac.lancs.networks.transients
DOC_PKGS += uk.ac.lancs.networks.persist
DOC_PKGS += uk.ac.lancs.networks.fabric
DOC_PKGS += uk.ac.lancs.networks.openflow
DOC_PKGS += uk.ac.lancs.networks.corsa
DOC_PKGS += uk.ac.lancs.networks.corsa.rest
DOC_PKGS += uk.ac.lancs.config
DOC_PKGS += uk.ac.lancs.agent
DOC_PKGS += uk.ac.lancs.rest

DOC_OVERVIEW=src/java-overview.html
DOC_CLASSPATH += $(jars:%=$(JARDEPS_OUTDIR)/%.jar)
DOC_SRC=$(call jardeps_srcdirs4jars,$(SELECTED_JARS))
DOC_CORE=dataplanebroker$(DOC_CORE_SFX)

installed-jars:: $(SELECTED_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
installed-jars:: $(SELECTED_JARS:%=$(JARDEPS_OUTDIR)/%-src.zip)

testdv: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -ea -cp "$(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar" TestDV

testoddspan: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -ea -cp "$(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar" TestOddSpan

testinitspan: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -ea -cp "$(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar" TestInitSpan

testdummy: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -ea -cp "$(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar" TestDummy

testinitdummy: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -ea -cp "$(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar" TestDummyInitiateTopology

testgeospan: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -ea -cp "$(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar" TestGeographicSpan

commander: all
	$(JAVA) -ea -cp "$(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/initiate-dpb-util.jar:$(JARDEPS_OUTDIR)/initiate-dpb-corsa.jar:$(subst $(jardeps_space),:,$(CLASSPATH))" uk.ac.lancs.networks.apps.Commander $(CONFIG) $(ARGS)

testcorsa: all
	$(JAVA) -ea -cp "$(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/initiate-dpb-util.jar:$(JARDEPS_OUTDIR)/initiate-dpb-corsa.jar:$(subst $(jardeps_space),:,$(CLASSPATH))" uk.ac.lancs.networks.corsa.rest.CorsaREST $(RESTAPI) $(CERTFILE) $(AUTHZFILE)

testconfig: all
	$(JAVA) -ea -cp "$(JARDEPS_OUTDIR)/initiate-dpb-util.jar" uk.ac.lancs.config.ConfigurationContext scratch/initiate-test-1corsa.properties slough-fabric-brperlink

blank:: clean
	$(RM) -r out

all:: installed-jars

install:: install-scripts
install:: install-jars

install-jars:: $(SELECTED_JARS:%=install-jar-%)
install-jar-%::
	@$(call JARDEPS_INSTALL,$(PREFIX)/share/java,$*,$(version_$*))

clean:: tidy

tidy::
	@$(PRINTF) 'Removing detritus\n'
	@$(FIND) . -name "*~" -delete

YEARS=2018

update-licence:
	$(FIND) . -name '.svn' -prune -or -type f -print0 | $(XARGS) -0 \
	$(SED) -i 's/Copyright (c) \s[0-9,]\+\sRegents of the University of Lancaster/Copyright $(YEARS), Regents of the University of Lancaster/g'
