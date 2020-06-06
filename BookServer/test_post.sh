#!/bin/bash
curl --header "Content-Type: application/json" \
  --request POST \
  --data @example_input.json \
  http://127.0.0.1:5000/
