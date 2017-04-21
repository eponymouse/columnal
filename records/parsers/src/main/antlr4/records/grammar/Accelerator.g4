grammar Accelerator;

KEY : ('a'..'z');
SHORTCUT_MODIFIER : 'C-';
ALT_MODIFIER : 'A-';

accelerator : SHORTCUT_MODIFIER? ALT_MODIFIER? KEY;