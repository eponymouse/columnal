#! /bin/bash
#
# Columnal: Safer, smoother data table processing.
# Copyright (c) Neil Brown, 2016-2020, 2022.
#
# This file is part of Columnal.
#
# Columnal is free software: you can redistribute it and/or modify it under
# the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.
#
# Columnal is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
# more details.
#
# You should have received a copy of the GNU General Public License along
# with Columnal. If not, see <https://www.gnu.org/licenses/>.
#

for i in {0..15}
do
  Xvfb :$((42 + i)) -screen 0 1280x1024x24 &
  sleep 1
done

for i in {0..15}
do
  icewm -d :$((42 + i)).0 &
  sleep 1
done

#DISPLAY=:42.0 xset -display :42.0 r off
#DISPLAY=:42.1 xset -display :42.1 r off
#DISPLAY=:42.2 xset -display :42.2 r off
#DISPLAY=:42.3 xset -display :42.3 r off
#DISPLAY=:42.4 xset -display :42.4 r off
#DISPLAY=:42.5 xset -display :42.5 r off
#DISPLAY=:42.6 xset -display :42.6 r off
#DISPLAY=:42.7 xset -display :42.7 r off
#DISPLAY=:42.8 xset -display :42.8 r off
#DISPLAY=:42.9 xset -display :42.9 r off
#DISPLAY=:42.10 xset -display :42.10 r off
#DISPLAY=:42.11 xset -display :42.11 r off
#DISPLAY=:42.12 xset -display :42.12 r off
#DISPLAY=:42.13 xset -display :42.13 r off
#DISPLAY=:42.14 xset -display :42.14 r off
#DISPLAY=:42.15 xset -display :42.15 r off
#
#xclock -display :42.0 -digital -update 1 -strftime ':42.0 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.1 -digital -update 1 -strftime ':42.1 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.2 -digital -update 1 -strftime ':42.2 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.3 -digital -update 1 -strftime ':42.3 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.4 -digital -update 1 -strftime ':42.0 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.5 -digital -update 1 -strftime ':42.1 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.6 -digital -update 1 -strftime ':42.2 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.7 -digital -update 1 -strftime ':42.3 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.8 -digital -update 1 -strftime ':42.0 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.9 -digital -update 1 -strftime ':42.1 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.10 -digital -update 1 -strftime ':42.2 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.11 -digital -update 1 -strftime ':42.3 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.12 -digital -update 1 -strftime ':42.0 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.13 -digital -update 1 -strftime ':42.1 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.14 -digital -update 1 -strftime ':42.2 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
#xclock -display :42.15 -digital -update 1 -strftime ':42.3 %Y-%m-%d %H:%M:%S' -geometry +100+100 -fg white -bg black &
