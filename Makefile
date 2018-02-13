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
roots_core += uk.ac.lancs.routing.span.Graphs
roots_core += uk.ac.lancs.routing.span.SpanningTreeComputer
roots_core += uk.ac.lancs.routing.span.DistanceVectorComputer
roots_core += uk.ac.lancs.switches.Network
roots_core += uk.ac.lancs.switches.aggregate.Aggregator
roots_core += uk.ac.lancs.switches.transients.DummyNetwork
roots_core += uk.ac.lancs.switches.transients.TransientAggregator

SELECTED_JARS += initiate-dpb-util
trees_initiate-dpb-util += util
roots_util += uk.ac.lancs.config.ConfigurationContext

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


DOC_PKGS += uk.ac.lancs.routing.metric
DOC_PKGS += uk.ac.lancs.routing.span
DOC_PKGS += uk.ac.lancs.switches
DOC_PKGS += uk.ac.lancs.switches.aggregate
DOC_PKGS += uk.ac.lancs.switches.transients
DOC_PKGS += uk.ac.lancs.switches.persist
DOC_PKGS += uk.ac.lancs.config

DOC_OVERVIEW=src/java-overview.html
DOC_CLASSPATH += $(jars:%=$(JARDEPS_OUTDIR)/%.jar)
DOC_SRC=$(call jardeps_srcdirs4jars,$(SELECTED_JARS))
DOC_CORE=dataplanebroker$(DOC_CORE_SFX)

all:: $(SELECTED_JARS:%=$(JARDEPS_OUTDIR)/%.jar)

testdv: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -cp $(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar TestDV

testoddspan: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -cp $(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar TestOddSpan

testinitspan: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -cp $(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar TestInitSpan

testdummy: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -cp $(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar TestDummy

testinitdummy: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -cp $(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar TestDummyInitiateTopology

testgeospan: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -cp $(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar TestGeographicSpan

testconfig: all
	$(JAVA) -cp $(JARDEPS_OUTDIR)/initiate-dpb-util.jar uk.ac.lancs.config.ConfigurationContext scratch/test.properties

#blank:: clean

clean:: tidy

tidy::
	@$(PRINTF) 'Removing detritus\n'
	@$(FIND) . -name "*~" -delete

YEARS=2017

update-licence:
	$(FIND) . -name '.svn' -prune -or -type f -print0 | $(XARGS) -0 \
	$(SED) -i 's/Copyright (c) \s[0-9,]\+\sRegents of the University of Lancaster/Copyright $(YEARS), Regents of the University of Lancaster/g'
