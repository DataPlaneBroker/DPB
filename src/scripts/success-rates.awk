BEGIN {
    FS = ",";
}

{
    algo = $1;
    grsz = $2;
    grinst = $3;
    glsz = $4;
    glinst = $5;
    delay = $6;
    score = $7;

    count[algo, grsz, glsz]++;
    success[algo, grsz, glsz] += 0;
    if (score != "Infinity") {
	success[algo, grsz, glsz]++;
	delayscores[algo, grsz, glsz] += delay * score;
	dsc[algo, grsz, glsz]++;
    }
    delays[algo, grsz, glsz] += delay;

    ALGO[algo];
    GRSZ[grsz];
    GLSZ[glsz];
}

END {
    for (algo in ALGO)
	for (grsz in GRSZ)
	    for (glsz in GLSZ)
		if ((algo, grsz, glsz) in count) {
		    printf "%s,%d,%d,%g,%d,%g,%g,%d\n", algo, grsz, glsz,
			success[algo, grsz, glsz] / count[algo, grsz, glsz],
			count[algo, grsz, glsz],
			delays[algo, grsz, glsz] / count[algo, grsz, glsz],
			delayscores[algo, grsz, glsz] / dsc[algo, grsz, glsz],
			dsc[algo, grsz, glsz];
		}
}

