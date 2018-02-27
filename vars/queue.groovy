def call(Map params = [:]) {
    Boolean condition = params.condition
    Boolean verbose = params.get('verbose', false)
    Integer linesize = params.get('linesize', 50)
    Closure body = params.fetcher
    String imageName = params.container
    String uuid = UUID.randomUUID().toString()

    if (!condition) {
        echo "Skipped"

        return
    }

    String options = "--dotsperline ${linesize}"

    if (verbose) {
        options = options.concat(" -v")
    }

    pod(label: "pubsub", containers: [
        containerTemplate(name: "fetcher", image: imageName, alwaysPullImage: true, resourceRequestCpu: '500m', resourceRequestMemory: '3500Mi'),
        containerTemplate(name: "gcloud", ttyEnabled: true, command: 'cat', image: "eu.gcr.io/akeneo-ci/gcloud:1.1", alwaysPullImage: true, resourceRequestCpu: '100m', resourceRequestMemory: '200Mi')
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

            Integer size = messages.size()
            Integer parallelism = params.get('parallelism', (size/10).setScale(0, java.math.RoundingMode.UP))

            writeJSON file: 'output.json', json: messages
            writeYaml file: 'k8s_job.yaml', data: [
                'apiVersion': 'batch/v1',
                'kind': 'Job',
                'metadata': ['name': NODE_NAME],
                'spec': [
                    'completions': size,
                    'parallelism': parallelism,
                    'template': [
                        'spec': [
                            'containers': [
                                [
                                    'name': 'main',
                                    'image': imageName,
                                    'imagePullPolicy': 'Always',
                                    'resources': ['requests': ['cpu': "900m", 'memory': "3500Mi"]],
                                    'env' : [
                                        ['name': 'BEHAT_SCREENSHOT_PATH', 'value': '/tmp/pod']
                                    ],
                                    'command': ["/bin/sh", "-c"],
                                    'args': ["/var/www/pim/bin/docker/start-servers; while true; do if [ -f \"/tmp/pod/main-terminated\" ]; then exit 0; fi; sleep 1; done"],
                                    'readinessProbe': [
                                        'timeoutSeconds': 5,
                                        'initialDelaySeconds': 2,
                                        'httpGet': [
                                            'path': '/user/login',
                                            'port': 80
                                        ]
                                    ],
                                    'volumeMounts': [
                                        ['name': 'tmp-pod', 'mountPath': '/tmp/pod']
                                    ]
                                ],
                                [
                                    'name': 'pubsub',
                                    'image': 'eu.gcr.io/akeneo-ci/gcloud:1.1',
                                    'imagePullPolicy': 'Always',
                                    'resources': ['requests': ['cpu': "100m", 'memory': "100Mi"]],
                                    'command': ["/bin/sh", "-c"],
                                    'args': [ "trap \"touch /tmp/pod/main-terminated\" EXIT; gcloud.phar pubsub:message:consume ${NODE_NAME}-subscription ${NODE_NAME}-results"],
                                    'env': [
                                        ['name': 'REDIS_URI', 'value': "tcp://redis.jenkins:6379"],
                                        ['name': 'POD_NAME', 'valueFrom': ['fieldRef': ['fieldPath': 'metadata.name']]],
                                        ['name': 'NAMESPACE', 'valueFrom': ['fieldRef': ['fieldPath': 'metadata.namespace']]]
                                    ],
                                    'volumeMounts': [
                                        ['name': 'tmp-pod', 'mountPath': '/tmp/pod']
                                    ]
                                ]
                            ],
                            'volumes': [
                                ['name': 'tmp-pod', 'emptyDir': ['medium': 'Memory']],
                            ],
                            'restartPolicy': 'Never'
                        ]
                    ]
                ]
            ]

            sh "gcloud.phar pubsub:message:publish ${NODE_NAME} output.json"

            try {
                sh "kubectl apply -f k8s_job.yaml"
                sh "gcloud.phar job:wait ${options} ${NODE_NAME}-results-subscription ${size} ${env.WORKSPACE} --ansi"
            } finally {
                sh "kubectl delete job ${NODE_NAME} || true"
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
