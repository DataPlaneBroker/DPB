BEGIN {
    FS = ",";
}

{
    if ($1 == KEY && ($2 == KEY2 || !KEY2))
	print $0;
}
