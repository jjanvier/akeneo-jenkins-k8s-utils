def testif(Map params = [:]) {
    Boolean condition = params.condition
    String script = params.script
    String imageName = params.container
    String path = params.get('junit', "")

    if (!condition) {
        echo "Skipped"

        return
    }

    pod(label: "test", containers: [
        containerTemplate(name: "main", image: imageName, resourceRequestCpu: '500m', resourceRequestMemory: '1000Mi')
    ]) {
        container("main") {
            try {
                sh script
            } finally {
                if (junit != "") {
                    junit path
                }
            }
        }
    }
}
