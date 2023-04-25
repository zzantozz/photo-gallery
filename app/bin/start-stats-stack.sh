#!/bin/bash -e

# Runs a grafana dashboard, etc, in docker. The app is instrumented to send metrics to it. Just start this, and
# go to http://localhost:3000. If you start this after the app is already running, the graphs will start off
# squirrelly, and you have to give it a little bit to settle down.
#
# Note: Graphite's "new metric create count" config value isn't overridden here, and last I checked, it's set to 50 per
# minute. This project is emitting over 1000 metrics, so it'll take a minimum of 20 minutes from startup for all graphs
# to populate!

top_dir="$(dirname "$(dirname "$(realpath "$0")")")"

docker-compose -f "$top_dir/docker/stats-stack.yml" up
