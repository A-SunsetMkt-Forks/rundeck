#!/bin/bash
set -e

rundeck_war_build() {
    echo "== Versions =="
    java -version
    echo "NPM=$(npm -version)"
    echo "Node=$(node --version)"
    echo "Groovy=$(groovy --version)"

    echo "== Launch War build =="
    ./gradlew -Penvironment="${ENV}" ${GRADLE_BUILD_OPTS} publishToMavenLocal build -x check
}

rundeck_assemble_build() {
    echo "== Versions =="
    java -version
    echo "NPM=$(npm -version)"
    echo "Node=$(node --version)"
    echo "Groovy=$(groovy --version)"

    echo "== Assemble build =="
    ./gradlew -Penvironment="${ENV}" ${GRADLE_BUILD_OPTS} rundeckapp:assemble
}

rundeck_gradle_tests() {
    ./gradlew -Penvironment="${ENV}" ${GRADLE_BUILD_OPTS} check
}

rundeck_gui_tests() {
    cd rundeckapp/grails-spa/packages/ui-trellis
    npm run ci:test:unit
}

rundeck_docker_build() {
    # Ensure the JRE version is set
    local jreVersion=${1}
    if [ -z "$jreVersion" ]; then
        echo "Error: jreVersion is not set"
        exit 1
    fi

    #Build image
    ./gradlew ${GRADLE_BASE_OPTS} officialBuild -Penvironment=${ENV} -PdockerRepository=${DOCKER_REPO} -PdockerTags=latest,SNAPSHOT -PjreVersion=${jreVersion}

    docker tag "${DOCKER_REPO}:latest" "${DOCKER_CI_REPO}:${DOCKER_IMAGE_BUILD_TAG}"

    # CircleCI tag builds do not have a branch set
    if [[ -n "${RUNDECK_BRANCH_CLEAN}" ]]; then
        local CI_BRANCH_TAG=${DOCKER_CI_REPO}:${RUNDECK_BRANCH_CLEAN}
        docker tag "${DOCKER_REPO}:latest" "$CI_BRANCH_TAG"
    fi

}

rundeck_docker_push() {
    docker_login

    docker push "${DOCKER_CI_REPO}:${DOCKER_IMAGE_BUILD_TAG}"

    # CircleCI tag builds do not have a branch set
    if [[ -n "${RUNDECK_BRANCH_CLEAN}" ]]; then
        local CI_BRANCH_TAG=${DOCKER_CI_REPO}:${RUNDECK_BRANCH_CLEAN}
        docker push "$CI_BRANCH_TAG"
    fi

    if [[ "${RUNDECK_MAIN_BUILD}" = true && -z "${RUNDECK_TAG}" ]]; then
        ./gradlew ${GRADLE_BASE_OPTS} officialPush -PdockerRepository=${DOCKER_REPO} -PdockerTags=SNAPSHOT
    fi
}

rundeck_docker_publish() {
    docker_login
    ./gradlew ${GRADLE_BASE_OPTS} -Penvironment="${ENV}" -PdockerRepository=${DOCKER_REPO} docker:officialPush
}

rundeck_verify_build() {
    ./gradlew ${GRADLE_BASE_OPTS} \
        -Penvironment="${ENV}" \
        verifyBuild
}

rundeck_gradle_functional_tests() {
    TEST_IMAGE=${TEST_IMAGE:-} ./gradlew :functional-test:${GRADLE_TASK} -Penvironment="${ENV}" -PtestFiles="${TEST_FILES}" ${GRADLE_BUILD_OPTS} --info
}