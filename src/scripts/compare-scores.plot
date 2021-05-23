set title sprintf("Relative scores for %d vertices", GRAPHSIZE)
set ylabel 'Score as fraction of U_{max}>1'
set xlabel 'Goal-set size'
set grid
#set xrange [0:300]
set datafile sep ','
plot sprintf("<(awk -f src/scripts/extract.awk -v KEY=u -v KEY2=%d scratch/comparisons.csv)", GRAPHSIZE) using 3:13 with linespoints ls 2 pt 3 title "U_{max}=1", \
sprintf("<(awk -f src/scripts/extract.awk -v KEY=um1em15 -v KEY2=%d scratch/comparisons.csv)", GRAPHSIZE) using 3:13 with linespoints ls 2 pt 4 title "U_{max}=1-10^{-15}", \
sprintf("<(awk -f src/scripts/extract.awk -v KEY=um1em12 -v KEY2=%d scratch/comparisons.csv)", GRAPHSIZE) using 3:13 with linespoints ls 2 pt 1 title "U_{max}=1-10^{-12}", \
sprintf("<(awk -f src/scripts/extract.awk -v KEY=um1em9 -v KEY2=%d scratch/comparisons.csv)", GRAPHSIZE) using 3:13 with linespoints ls 2 pt 6 title "U_{max}=1-10^{-9}"
