all::

FIND=find
SED=sed
XARGS=xargs
PRINTF=printf

-include dataplanebroker-env.mk

PROJECT_JAVACFLAGS += -Xlint:unchecked

jars += $(COMMON_JARS)
jars += $(SERVER_JARS)
jars += $(TEST_JARS)

COMMON_JARS += initiate-dpb-core
trees_initiate-dpb-core += core
deps_core += util
roots_core=$(found_core)

SERVER_JARS += initiate-dpb-server
trees_initiate-dpb-server += server
deps_server += core
deps_server += util
roots_server=$(found_server)

COMMON_JARS += initiate-dpb-corsa
trees_initiate-dpb-corsa += corsa
deps_corsa += util
deps_corsa += core
roots_corsa=$(found_corsa)

COMMON_JARS += initiate-dpb-openflow
trees_initiate-dpb-openflow += openflow
deps_openflow += util
deps_openflow += core
roots_openflow=$(found_openflow)

COMMON_JARS += initiate-dpb-util
trees_initiate-dpb-util += util
roots_util=$(found_util)

TEST_JARS += tests
roots_tests += TestOddSpan
roots_tests += TestInitSpan
roots_tests += TestDummy
roots_tests += TestDV
roots_tests += TestDummyInitiateTopology
roots_tests += TestGeographicSpan
roots_tests += TestJsonServer
roots_tests += TestJsonClient
roots_tests += AlgoPerfTest
roots_tests += TopologyDemonstration
jdeps_tests += initiate-dpb-core

JARDEPS_OUTDIR=out
JARDEPS_SRCDIR=src/java-tree
JARDEPS_MERGEDIR=src/java-merge

-include jardeps.mk
-include jardeps-install.mk

SHAREDIR=$(PREFIX)/share/dataplane-broker

datafiles += portslicer.py
datafiles += tupleslicer.py

scripts += dpb-server
scripts += dpb-client
scripts += dpb-ssh-agent


-include binodeps.mk


DOC_PKGS += uk.ac.lancs.routing.metric
DOC_PKGS += uk.ac.lancs.routing.span
DOC_PKGS += uk.ac.lancs.networks
DOC_PKGS += uk.ac.lancs.networks.apps
DOC_PKGS += uk.ac.lancs.networks.apps.server
DOC_PKGS += uk.ac.lancs.networks.jsoncmd
DOC_PKGS += uk.ac.lancs.networks.mgmt
DOC_PKGS += uk.ac.lancs.networks.rest
DOC_PKGS += uk.ac.lancs.networks.transients
DOC_PKGS += uk.ac.lancs.networks.persist
DOC_PKGS += uk.ac.lancs.networks.fabric
DOC_PKGS += uk.ac.lancs.networks.openflow
DOC_PKGS += uk.ac.lancs.networks.corsa
DOC_PKGS += uk.ac.lancs.networks.corsa.rest
DOC_PKGS += uk.ac.lancs.config
DOC_PKGS += uk.ac.lancs.agent

DOC_OVERVIEW=src/java-overview.html
DOC_CLASSPATH += $(jars:%=$(JARDEPS_OUTDIR)/%.jar)
DOC_SRC=$(call jardeps_srcdirs4jars,$(jars))
DOC_CORE=dataplanebroker$(DOC_CORE_SFX)

common-jars:: $(COMMON_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
common-jars:: $(COMMON_JARS:%=$(JARDEPS_OUTDIR)/%-src.zip)

server-jars:: common-jars
server-jars:: $(SERVER_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
server-jars:: $(SERVER_JARS:%=$(JARDEPS_OUTDIR)/%-src.zip)

client-jars:: common-jars

testalgoperf:  all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -ea -cp "$(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar" AlgoPerfTest

topodemo:  all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -ea -cp "$(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar" TopologyDemonstration

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

all:: client server
client:: client-jars
server:: server-jars

install:: install-data
install:: install-scripts
install:: install-jars

install-ctrl:: install-data
install-ssh:: install-scripts
install-client:: install-scripts install-client-jars
install-server:: install-scripts install-server-jars

install-common-jars:: $(COMMON_JARS:%=install-jar-%)
install-server-jars install-client-jars:: install-common-jars
install-server-jars:: $(SERVER_JARS:%=install-jar-%)

install-jars:: install-client-jars install-server-jars
install-jar-%::
	@$(call JARDEPS_INSTALL,$(PREFIX)/share/java,$*,$(version_$*))

clean:: tidy

tidy::
	@$(PRINTF) 'Removing detritus\n'
	@$(FIND) . -name "*~" -delete

YEARS=2018,2019

update-licence:
	$(FIND) . -name '.svn' -prune -or -type f -print0 | $(XARGS) -0 \
	$(SED) -i 's/Copyright\s\+[0-9,]\+\sRegents of the University of Lancaster/Copyright $(YEARS), Regents of the University of Lancaster/g'
