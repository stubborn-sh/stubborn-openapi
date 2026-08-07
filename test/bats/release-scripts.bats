#!/usr/bin/env bats
#
# Tests for the release helper scripts. These orchestrate git/mvn, so the
# testable surface is argument validation: each script must refuse to run (and
# thus never touch git history or push) when its required argument is missing.

TAG_AND_PUSH="${BATS_TEST_DIRNAME}/../../scripts/release/tag-and-push.sh"
BACK_TO_SNAPSHOT="${BATS_TEST_DIRNAME}/../../scripts/release/back-to-snapshot.sh"

@test "tag-and-push refuses to run without a version" {
	run bash "$TAG_AND_PUSH"
	[ "$status" -ne 0 ]
	[[ "$output" == *"release version required"* ]]
}

@test "back-to-snapshot refuses to run without a next-snapshot version" {
	run bash "$BACK_TO_SNAPSHOT"
	[ "$status" -ne 0 ]
	[[ "$output" == *"next snapshot version required"* ]]
}
