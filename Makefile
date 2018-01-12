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
roots_core += uk.ac.lancs.routing.span.Spans
roots_core += uk.ac.lancs.switches.SwitchManagement
roots_core += uk.ac.lancs.switches.DummySwitch
roots_core += uk.ac.lancs.switches.aggregate.TransientAggregator

TEST_JARS += tests
roots_tests += TestOddSpan
roots_tests += TestInitSpan
roots_tests += TestDummy
roots_tests += TestDummyInitiateTopology
deps_tests += core

JARDEPS_OUTDIR=out
JARDEPS_SRCDIR=src/java-tree
JARDEPS_MERGEDIR=src/java-merge

include jardeps.mk

all:: $(SELECTED_JARS:%=$(JARDEPS_OUTDIR)/%.jar)

testoddspan: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -cp $(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar TestOddSpan

testinitspan: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -cp $(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar TestInitSpan

testdummy: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -cp $(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar TestDummy

testinitdummy: all $(TEST_JARS:%=$(JARDEPS_OUTDIR)/%.jar)
	$(JAVA) -cp $(JARDEPS_OUTDIR)/initiate-dpb-core.jar:$(JARDEPS_OUTDIR)/tests.jar TestDummyInitiateTopology

#blank:: clean

clean:: tidy

tidy::
	@$(PRINTF) 'Removing detritus\n'
	@$(FIND) . -name "*~" -delete

YEARS=2017

update-licence:
	$(FIND) . -name '.svn' -prune -or -type f -print0 | $(XARGS) -0 \
	$(SED) -i 's/Copyright (c) \s[0-9,]\+\sRegents of the University of Lancaster/Copyright $(YEARS), Regents of the University of Lancaster/g'
