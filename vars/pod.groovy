def call(Map params = [:], Closure body) {
    String label = params.get('label', null)
    Array containers = params.get('containers', []])
    Array annotations = params.get('annotations', [])
    Array volumes = params.get('volumes', [])

    String uuid = UUID.randomUUID().toString()

    // see https://issues.jenkins-ci.org/browse/JENKINS-42184
    currentBuild.rawBuild.getAction( PodTemplateAction.class )?.stack?.clear()

    timeout(time: 4, unit: HOURS) {
        return steps.podTemplate(label: label + "-" + uuid, containers: containers, annotations: annotations, volumes: volumes) {
            node(label + "-" + uuid) {
                body()
            }
        }
    }
}
