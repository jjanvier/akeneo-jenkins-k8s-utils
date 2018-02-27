def call(Map params = [:]) {
    Boolean condition = params.condition
    String script = params.script
    String imageName = params.container
    String junitPath = params.get('junit', '')

    if (!condition) {
        echo "Skipped"

        return
    }

    pod(label: "test", containers: [
        containerTemplate(name: "main", image: imageName, alwaysPullImage: true, resourceRequestCpu: '900m', resourceRequestMemory: '3500Mi')
    ]) {
        container("main") {
            try {
                sh script.replace("%workspace%", env.WORKSPACE)
            } finally {
                if (junitPath != "") {
                    junit junitPath
                }
            }
        }
    }
}
