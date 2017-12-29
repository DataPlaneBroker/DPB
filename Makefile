all::

FIND ?= find

-include treeroute-env.mk

jars += core

SELECTED_JARS += core

roots_core += uk.ac.lancs.treespan.Spans

JARDEPS_OUTDIR=out
JARDEPS_SRCDIR=src/java-tree
JARDEPS_MERGEDIR=src/java-merge

include jardeps.mk

all:: $(SELECTED_JARS:%=$(JARDEPS_OUTDIR)/%.jar)

blank:: clean
	$(RM) -r bin

clean:: tidy

tidy::
	$(FIND) . -name "*~" -delete
