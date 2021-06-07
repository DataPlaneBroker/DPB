set title 'Score-delay outperformance vs U_{max}>1'
set ylabel 'Percentage of cases better than U_{max}>1'
set xlabel 'Goal-set size'
set grid
set key bottom right font ",8.2"
#set xrange [0:300]
set yrange [0:100]
set datafile sep ','
plot "<(awk -f src/scripts/extract.awk -v KEY=u -v KEY2=20 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 0 pt 3 title "U_{max}=1, V=20", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em15 -v KEY2=20 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 0 pt 4 title "U_{max}=1-10^{-15}, V=20", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em12 -v KEY2=20 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 0 pt 1 title "U_{max}=1-10^{-12}, V=20", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em9 -v KEY2=20 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 0 pt 6 title "U_{max}=1-10^{-9}, V=20", \
\
"<(awk -f src/scripts/extract.awk -v KEY=u -v KEY2=50 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 2 pt 3 title "U_{max}=1, V=50", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em15 -v KEY2=50 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 2 pt 4 title "U_{max}=1-10^{-15}, V=50", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em12 -v KEY2=50 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 2 pt 1 title "U_{max}=1-10^{-12}, V=50", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em9 -v KEY2=50 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 2 pt 6 title "U_{max}=1-10^{-9}, V=50", \
\
"<(awk -f src/scripts/extract.awk -v KEY=u -v KEY2=80 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 1 pt 3 title "U_{max}=1, V=80", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em15 -v KEY2=80 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 1 pt 4 title "U_{max}=1-10^{-15}, V=80", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em12 -v KEY2=80 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 1 pt 1 title "U_{max}=1-10^{-12}, V=80", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em9 -v KEY2=80 scratch/comparisons.csv)" using 3:($20*100) with linespoints ls 1 pt 6 title "U_{max}=1-10^{-9}, V=80"
