grammar Accelerator;

KEY : ('a'..'z') | 'Esc';
SHORTCUT_MODIFIER : 'C-';
ALT_MODIFIER : 'A-';
SHIFT_MODIFIER : 'S-';

accelerator : SHORTCUT_MODIFIER? ALT_MODIFIER? SHIFT_MODIFIER? KEY;