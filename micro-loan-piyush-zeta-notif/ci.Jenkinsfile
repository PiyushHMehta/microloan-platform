@Library("ci@master") _

MavenDockerPublish(
     'tenant': 'omega',
     'jdkVersion': '17',
     'mavenDeployCommand': 'mvn clean package -DskipTests',
     'enableCodeSonarSastScan': false,
     'enableCodeSonarSastGating': false,
     'codeSonarScanCommand': '',
     'disableImagePublish': false
)
