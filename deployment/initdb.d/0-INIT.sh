#!/usr/bin/env bash
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'cvk'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE cvk"
