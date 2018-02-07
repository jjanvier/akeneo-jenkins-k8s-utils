def call(Map params = [:], Closure body) {
    Boolean condition = params.condition
    Boolean verbose = params.get('verbose', false)
    Integer linesize = params.get('dots', 50)
    String imageName = params.image
    String uuid = UUID.randomUUID().toString()

    if (!condition) {
        echo "Skipped"

        return
    }

    pod(label: "pubsub", containers: [
        containerTemplate(name: "fetcher", image: imageName, resourceRequestCpu: '500m', resourceRequestMemory: '500Mi'),
        containerTemplate(name: "gcloud", ttyEnabled: true, command: 'cat', image: "eu.gcr.io/akeneo-ci/gcloud:1.0", alwaysPullImage: true, resourceRequestCpu: '100m', resourceRequestMemory: '200Mi')
    ]) {
        def messages = []

        container("fetcher") {
            messages = body()
        }

        container("gcloud") {
            sh "gcloud.phar pubsub:topic:create ${NODE_NAME}"
            sh "gcloud.phar pubsub:topic:create ${NODE_NAME}-results"
            sh "gcloud.phar pubsub:subscription:create ${NODE_NAME} ${NODE_NAME}-subscription"
            sh "gcloud.phar pubsub:subscription:create ${NODE_NAME}-results ${NODE_NAME}-results-subscription"

            def size = messages.size()

            writeJSON file: 'output.json', json: messages
            sh "gcloud.phar pubsub:message:publish ${NODE_NAME} output.json"

            sh "sed -i 's#JOB_SCALE#${scale}#g' /home/jenkins/pim/.ci/k8s/pubsub_consumer_job.yaml"
            sh "sed -i 's#JOB_NAME#${NODE_NAME}#g' /home/jenkins/pim/.ci/k8s/pubsub_consumer_job.yaml"
            sh "sed -i 's#JOB_COMPLETIONS#${size}#g' /home/jenkins/pim/.ci/k8s/pubsub_consumer_job.yaml"
            sh "sed -i 's#SUBSCRIPTION_NAME#${NODE_NAME}-subscription#g' /home/jenkins/pim/.ci/k8s/pubsub_consumer_job.yaml"
            sh "sed -i 's#RESULT_TOPIC#${NODE_NAME}-results#g' /home/jenkins/pim/.ci/k8s/pubsub_consumer_job.yaml"
            sh "sed -i 's#PIM_IMAGE#eu.gcr.io/akeneo-ci/pim-community-dev:pull-request-${env.CHANGE_ID}-build-${env.BUILD_NUMBER}-${edition}#g' /home/jenkins/pim/.ci/k8s/pubsub_consumer_job.yaml"

            try {
                sh "kubectl apply -f /home/jenkins/pim/.ci/k8s/"
                sh "gcloud.phar ${verbosity} job:wait --dotsperline ${linesize} ${NODE_NAME}-results-subscription ${size} ${env.WORKSPACE} --ansi"
            } finally {
                sh "kubectl delete job ${NODE_NAME} --grace-period=0 || true"
                sh "gcloud.phar pubsub:topic:delete ${NODE_NAME} || true"
                sh "gcloud.phar pubsub:topic:delete ${NODE_NAME}-results || true"
                sh "gcloud.phar pubsub:subscription:delete ${NODE_NAME}-subscription || true"
                sh "gcloud.phar pubsub:subscription:delete ${NODE_NAME}-results-subscription || true"

                junit allowEmptyResults: true, testResults: 'junit/**/*.xml'
                archiveArtifacts allowEmptyArchive: true, artifacts: 'artifacts/**/*.png'
            }
        }
    }
}
