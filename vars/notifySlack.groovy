def call(String slackChannel) {
    if (slackChannel == '') {
        return;
    }

    // Null status means success
    buildStatus = currentBuild.result

    String color = '#42C02D'
    if (buildStatus != null) {
        color = '#FF0000'
    }

    def msg = "<${env.BUILD_URL}|Build finished:> `${env.JOB_NAME}`"
    slackSend(color: color, message: msg, channel:"${slackChannel}")
}
