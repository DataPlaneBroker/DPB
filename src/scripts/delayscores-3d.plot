set dgrid3d 30,30
set hidden3d
set zlabel 'Score-completion time (bs)'
set ylabel 'Graph size (vertices)'
set xlabel 'Goal-set size'
#set grid
#set xrange [0:300]
#set zrange [0.4:1.0]
set datafile sep ','
set logscale z
splot "<(awk -f src/scripts/extract.awk -v KEY=exh scratch/success-rates.csv)" using 3:2:7 with lines title "U_{max}>1", \
"<(awk -f src/scripts/extract.awk -v KEY=u scratch/success-rates.csv)" using 3:2:7 with lines title "U_{max}=1", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em15 scratch/success-rates.csv)" using 3:2:7 with lines title "U_{max}=1-10^{-15}", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em12 scratch/success-rates.csv)" using 3:2:7 with lines title "U_{max}=1-10^{-12}", \
"<(awk -f src/scripts/extract.awk -v KEY=um1em9 scratch/success-rates.csv)" using 3:2:7 with lines title "U_{max}=1-10^{-9}"
