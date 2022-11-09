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

# Call like:
#   bash manage-xvfb-screens.sh start N videoSlug
#   bash manage-xvfb-screens.sh stop N videoSlug
# Where N is the number of the X display.  It will automatically start/stop
# Xvfb, icewm, and ffmpeg for that display number, using videoSlug to name the output file

case $1 in
  start)
    Xvfb :"$2" -screen 0 1280x1024x24 &
    echo $! > processes-"$2"-xvfb.pid
    sleep 5
    icewm -d :"$2".0 &
    echo $! > processes-"$2"-icewm.pid
    sleep 5
    ffmpeg -nostdin -y -video_size 1280x1024 -framerate 8 -f x11grab -i :"$2".0 -codec:v libx264rgb -preset ultrafast -vf drawtext=text='%{localtime\:%T}':x=100:y=50:fontcolor=white:fontsize=30 recording-"$3".mp4 > "$3".out.ffmpeg.log 2> "$3".err.ffmpeg.log &
    echo $! > processes-"$2"-ffmpeg.pid
    ;;
  stop)
    # Tell FFMPEG to stop recording:
    kill -SIGINT $(cat processes-"$2"-ffmpeg.pid)
    # Give it time to finish:
    sleep 10
    kill $(cat processes-"$2"-icewm.pid)
    sleep 5
    kill $(cat processes-"$2"-xvfb.pid)
    ;;
esac
