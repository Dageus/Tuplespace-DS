################################################
### PATHS (feel free to tweak paths accordingly)
CLI_PATH=${PWD}/../Client
PUBLIC_TESTS_FOLDER=${PWD}/public_tests
PUBLIC_TESTS_OUT_EXPECTED=${PUBLIC_TESTS_FOLDER}/expected
PUBLIC_TESTS_OUTPUT=${PUBLIC_TESTS_FOLDER}/test-outputs
SYNC_TESTS_FOLDER=${PWD}/sync_tests
SYNC_TESTS_OUT_EXPECTED=${SYNC_TESTS_FOLDER}/expected
SYNC_TESTS_OUTPUT=${SYNC_TESTS_FOLDER}/test-outputs
################################################
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'
################################################

rm -rf $PUBLIC_TESTS_OUTPUT
mkdir -p $PUBLIC_TESTS_OUTPUT
rm -rf $SYNC_TESTS_OUTPUT
mkdir -p $SYNC_TESTS_OUTPUT

cd $CLI_PATH
mvn --quiet compile
for i in {1..5}
do
    TEST=$(printf "%02d" $i)
    mvn --quiet exec:java < ${PUBLIC_TESTS_FOLDER}/input$TEST.txt > ${PUBLIC_TESTS_OUTPUT}/out$TEST.txt

    DIFF=$(diff ${PUBLIC_TESTS_OUTPUT}/out$TEST.txt ${PUBLIC_TESTS_OUT_EXPECTED}/out$TEST.txt)
    if [ "$DIFF" != "" ]
    then
        echo -e "${RED}[$TEST] TEST FAILED${NC}"
    else
        echo -e "${GREEN}[$TEST] TEST PASSED${NC}"
    fi
done


# Function to run each test
run_test() {
    local TEST="$(printf "%02d" "$1")"
    local CLIENT="$(printf "%02d" "$2")"
    mvn --quiet exec:java  < "${SYNC_TESTS_FOLDER}/input${TEST}_client${CLIENT}.txt" > "${SYNC_TESTS_OUTPUT}/out${TEST}_client${CLIENT}.txt"

    local DIFF=$(diff "${SYNC_TESTS_OUTPUT}/out${TEST}_client${CLIENT}.txt" "${SYNC_TESTS_OUT_EXPECTED}/out${TEST}_client${CLIENT}.txt")
    if [ "$DIFF" != "" ]; then
        echo -e "${RED}[${TEST}-${CLIENT}] TEST FAILED${NC}"
    else
        echo -e "${GREEN}[${TEST}-${CLIENT}] TEST PASSED${NC}"
    fi
}

# Run tests in parallel
for i in {1..3}
do
  for j in {1..3}
    do
        run_test "$i" "$j" &
    done
  wait
done


echo "Check the outputs of each public test in ${PUBLIC_TESTS_OUTPUT}."
echo "Check the outputs of each synchronized test in ${SYNC_TESTS_OUTPUT}."
