#!/usr/bin/env bash
# Asserts production-packaging invariants of a node's shipped jar — the things dev runs and
# gametests can never see, because they run from compiled classes and dev-applied ATs/AWs,
# not from the packaged jar. Each check here corresponds to a bug that once shipped:
# wrong class version for the era's stock Java, an invalid shade package name, a missing
# META-INF/accesstransformer.cfg (IllegalAccessError in production only).
#
# Usage: .github/scripts/verify_shipped_jar.sh <node>     e.g. tools/verify_shipped_jar.sh 1.20.1-forge
set -euo pipefail

node="$1"
version="${node%-*}"
loader="${node##*-}"
libs="versions/$node/build/libs"

fail() { echo "FAIL($node): $*" >&2; exit 1; }

jar=$(ls "$libs"/*.jar | grep -vE -- '-(dev|downgraded|sources|javadoc)\.jar$' || true)
[ -n "$jar" ] || fail "no shipped jar in $libs"
[ "$(printf '%s\n' "$jar" | wc -l)" -eq 1 ] || fail "expected exactly one shipped jar, got: $jar"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q "$jar" -d "$work"

# 1. Bytecode must load on the era's stock Java (25 / 21 / 17).
case "$version" in
  26*)   want=69 ;;
  1.21*) want=65 ;;
  1.20*) want=61 ;;
  *) fail "no expected class version for '$version'" ;;
esac
cls="$work/com/skilles/chronoclones/Chronoclones.class"
[ -f "$cls" ] || fail "missing $cls"
major=$(od -An -j 6 -N 2 -t u1 "$cls" | awk '{print $1 * 256 + $2}')
[ "$major" -eq "$want" ] || fail "class major version $major, want $want"

# 2. Every top-level package must be a valid Java identifier (shade-path regression guard).
for d in "$work"/*/; do
  b=$(basename "$d")
  case "$b" in META-INF|assets|data) continue ;; esac
  printf '%s' "$b" | grep -qE '^[A-Za-z_][A-Za-z0-9_]*$' \
    || fail "top-level package '$b' is not a valid Java identifier"
done

# 3. Loader-specific metadata.
case "$loader" in
  forge)
    [ -f "$work/META-INF/accesstransformer.cfg" ] || fail "missing META-INF/accesstransformer.cfg"
    grep -q 'f_[0-9]' "$work/META-INF/accesstransformer.cfg" || fail "accesstransformer.cfg has no srg names"
    grep -q 'mandatory=true' "$work/META-INF/mods.toml" || fail "mods.toml lacks mandatory=true"
    [ -f "$work/pack.mcmeta" ] || fail "missing pack.mcmeta"
    ;;
  neoforge)
    [ -f "$work/META-INF/accesstransformer.cfg" ] || fail "missing META-INF/accesstransformer.cfg"
    [ -f "$work/META-INF/neoforge.mods.toml" ] || fail "missing META-INF/neoforge.mods.toml"
    ;;
  fabric)
    [ -f "$work/fabric.mod.json" ] || fail "missing fabric.mod.json"
    case "$version" in
      26*)   wantjava=">=25" ;;
      1.21*) wantjava=">=21" ;;
      1.20*) wantjava=">=17" ;;
    esac
    grep -q "\"java\": \"$wantjava\"" "$work/fabric.mod.json" \
      || fail "fabric.mod.json java gate is not $wantjava"
    # Pre-26 production Fabric is intermediary-named; the AW must have been remapped with it.
    case "$version" in
      26*) ;;
      *) head -1 "$work/chronoclones.accesswidener" | grep -q intermediary \
           || fail "access widener is not in intermediary names" ;;
    esac
    ;;
  *) fail "unknown loader '$loader'" ;;
esac

echo "OK($node): $(basename "$jar") verified (class $major)"
