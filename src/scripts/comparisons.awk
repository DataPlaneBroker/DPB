BEGIN {
    FS = ",";
    STDALGO = "exh";
}

{
    algo = $1;
    grsz = $2;
    grinst = $3;
    glsz = $4;
    glinst = $5;
    delay = $6;
    score = $7;

    DELAY[grsz, grinst, glsz, glinst, algo] = delay;
    if (score != "Infinity")
	SCORE[grsz, grinst, glsz, glinst, algo] = score;

    ALGO[algo];
    GRSZ[grsz];
    GRINST[grinst];
    GLSZ[glsz];
    GLINST[glinst];    
}

END {
    delete ALGO[STDALGO];

    for (grsz in GRSZ) {
	for (glsz in GLSZ) {
	    for (grinst in GRINST) {
		for (glinst in GLINST) {
		    for (algo in ALGO) {
			## How much faster is the alternative
			## algorithm compared to the reference?
			delay = DELAY[grsz, grinst, glsz, glinst, algo];
			delay /= DELAY[grsz, grinst, glsz, glinst, STDALGO];

			DELAYSUM[grsz, glsz, algo] += delay;
			DELAYSUMSQ[grsz, glsz, algo] += delay * delay;
			DELAYCOUNT[grsz, glsz, algo]++;

			if ((grsz, grinst, glsz, glinst, STDALGO) in SCORE) {
			    ## The reference algorithm succeeded.
			    if ((grsz, grinst, glsz, glinst, algo) in SCORE) {
				## Both reference and alternative
				## algorithm succeeded.
				# printf "%s and %s succeeded\n",
				#     STDALGO, algo > "/dev/stderr";
				score = 1;
				score /= \
				    SCORE[grsz, grinst, glsz, glinst, STDALGO];
				score *= \
				    SCORE[grsz, grinst, glsz, glinst, algo];

				SCORESUM[grsz, glsz, algo] += score;
				SCORESUMSQ[grsz, glsz, algo] += score * score;

				product = score * delay;
				PRODSUM[grsz, glsz, algo] += product;
				PRODSUMSQ[grsz, glsz, algo] += product * product;
				PRODCOUNT[grsz, glsz, algo]++;
			    } else {
				## The reference algorithm succeeded
				## but the alternative did not.
				# printf "%s succeeded where %s failed\n",
				#     STDALGO, algo > "/dev/stderr";
				STDBETTER[grsz, glsz, algo]++;
			    }
			} else if ((grsz, grinst, glsz, glinst, algo) in SCORE) {
			    ## The alternative algorithm succeeded
			    ## when the reference algorithm did not.
			    # printf "%s failed where %s succeeded\n",
			    # 	STDALGO, algo > "/dev/stderr";
			    ALTBETTER[grsz, glsz, algo]++;
			} else {
			    ## Both reference and alternative
			    ## algorithms failed.
			    # printf "both %s and %s failed\n",
			    # 	STDALGO, algo > "/dev/stderr";
			    BOTHFAILED[grsz, glsz, algo]++;
			}
		    }
		}
	    }
	}
    }
    
    for (algo in ALGO)
	for (grsz in GRSZ)
	    for (glsz in GLSZ) {
		mean = DELAYMEAN[grsz, glsz, algo] =				\
		    DELAYSUM[grsz, glsz, algo] / DELAYCOUNT[grsz, glsz, algo];
		meansq = \
		    DELAYSUMSQ[grsz, glsz, algo] / DELAYCOUNT[grsz, glsz, algo];
		DELAYSD[grsz, glsz, algo] = sqrt(meansq - mean * mean);

		mean = SCOREMEAN[grsz, glsz, algo] =			\
		    SCORESUM[grsz, glsz, algo] / PRODCOUNT[grsz, glsz, algo];
		meansq = \
		    SCORESUMSQ[grsz, glsz, algo] / PRODCOUNT[grsz, glsz, algo];
		SCORESD[grsz, glsz, algo] = sqrt(meansq - mean * mean);

		mean = PRODMEAN[grsz, glsz, algo] =			\
		    PRODSUM[grsz, glsz, algo] / PRODCOUNT[grsz, glsz, algo];
		meansq = \
		    PRODSUMSQ[grsz, glsz, algo] / PRODCOUNT[grsz, glsz, algo];
		PRODSD[grsz, glsz, algo] = sqrt(meansq - mean * mean);
	    }

    for (algo in ALGO)
	for (grsz in GRSZ)
	    for (glsz in GLSZ) {
		printf "%s,%d,%d", algo, grsz, glsz;
		printf ",%d", DELAYCOUNT[grsz, glsz, algo];
		printf ",%g", PRODCOUNT[grsz, glsz, algo] /	\
		    DELAYCOUNT[grsz, glsz, algo];
		printf ",%g", STDBETTER[grsz, glsz, algo] /	\
		    DELAYCOUNT[grsz, glsz, algo];
		printf ",%g", ALTBETTER[grsz, glsz, algo] /	\
		    DELAYCOUNT[grsz, glsz, algo];
		printf ",%g", BOTHFAILED[grsz, glsz, algo] /	\
		    DELAYCOUNT[grsz, glsz, algo];
		printf ",%d,%g,%g", DELAYCOUNT[grsz, glsz, algo],
		    DELAYMEAN[grsz, glsz, algo], DELAYSD[grsz, glsz, algo];
		printf ",%d,%g,%g", PRODCOUNT[grsz, glsz, algo],
		    SCOREMEAN[grsz, glsz, algo], SCORESD[grsz, glsz, algo];
		printf ",%d,%g,%g", PRODCOUNT[grsz, glsz, algo],
		    PRODMEAN[grsz, glsz, algo], PRODSD[grsz, glsz, algo];
		printf "\n";
	    }
}

