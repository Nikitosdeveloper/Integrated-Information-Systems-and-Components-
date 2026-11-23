#!/bin/bash
# Использование: scripts/gql.sh '<query>' [token]
Q=$(python3 -c "import json,sys; print(json.dumps({'query': sys.argv[1]}))" "$1")
if [ -n "$2" ]; then H="Authorization: Bearer $2"; else H="X-None: 1"; fi
curl -s -X POST localhost:8080/graphql -H 'Content-Type: application/json' -H "$H" -d "$Q"
