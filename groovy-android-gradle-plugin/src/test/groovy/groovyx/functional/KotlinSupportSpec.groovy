/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovyx.functional

import spock.lang.Unroll

import static groovyx.internal.TestProperties.buildToolsVersion
import static groovyx.internal.TestProperties.compileSdkVersion

/**
 * Allows Kotlin and Groovy to play nicely with each other.
 * https://github.com/groovy/groovy-android-gradle-plugin/issues/139
 */
class KotlinSupportSpec extends FunctionalSpec {

  @Unroll
  def "should compile with kotlin dependencies with kotlin version: #kotlinVersion, android plugin:#androidPluginVersion and gradle version:#gradleVersion"() {
    given:
    file("settings.gradle") << "rootProject.name = 'test-app'"

    buildFile << """
      buildscript {
        repositories {
          maven { url "${localRepo.toURI()}" }    
          $googleMavenRepo
          jcenter()
        }
        dependencies {
          classpath 'com.android.tools.build:gradle:$androidPluginVersion'
          classpath 'org.codehaus.groovy:groovy-android-gradle-plugin:$PLUGIN_VERSION'
          classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion'
        }
      }

      apply plugin: 'com.android.application'
      apply plugin: 'kotlin-android' // must be applied before groovy
      apply plugin: 'groovyx.android'

      repositories {
        $googleMavenRepo
        jcenter()
      }

      android {
        compileSdkVersion $compileSdkVersion
        buildToolsVersion '$buildToolsVersion'

        defaultConfig {
          minSdkVersion 16
          targetSdkVersion $compileSdkVersion

          versionCode 1
          versionName '1.0.0'

          testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'
        }

        buildTypes {
          debug {
            applicationIdSuffix '.dev'
          }
        }

        compileOptions {
          sourceCompatibility '1.7'
          targetCompatibility '1.7'
        }
      }

      dependencies {
        compile 'org.codehaus.groovy:groovy:2.4.11:grooid'
        compile 'org.jetbrains.kotlin:kotlin-stdlib-jre7:$kotlinVersion'

        androidTestCompile 'com.android.support.test:runner:0.5'
        androidTestCompile 'com.android.support.test:rules:0.5'

        testCompile 'junit:junit:4.12'
      }

      // force unit test types to be assembled too
      android.testVariants.all { variant ->
        tasks.getByName('assemble').dependsOn variant.assemble
      }
    """

    file('src/main/AndroidManifest.xml') << """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="groovyx.test">

        <application
            android:allowBackup="true"
            android:label="Test App">
          <activity
              android:name=".MainActivity"
              android:label="Test App">
            <intent-filter>
              <action android:name="android.intent.action.MAIN"/>
              <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
          </activity>
        </application>

      </manifest>
    """.trim()

    file('src/main/res/layout/activity_main.xml') << """
      <?xml version="1.0" encoding="utf-8"?>
      <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent"
      >

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Hello Groovy!"
            android:gravity="center"
            android:textAppearance="?android:textAppearanceLarge"
        />

      </FrameLayout>
    """.trim()

    file('src/main/groovy/groovyx/test/MainActivity.groovy') << """
      package groovyx.test

      import android.app.Activity
      import android.os.Bundle
      import groovy.transform.CompileStatic

      @CompileStatic
      class MainActivity extends Activity {
        @Override void onCreate(Bundle savedInstanceState) {
          super.onCreate(savedInstanceState)
          contentView = R.layout.activity_main
          
          def value = new SimpleTest().getValue()
        }
      }
    """

    file('src/main/java/groovyx/test/SimpleTest.kt') << """
      package groovyx.test

      class SimpleTest {
        val value: String get() = "Hello World"
      }
    """

    file('src/test/groovy/groovyx/test/JvmTest.groovy') << """
      package groovyx.test

      import org.junit.Test

      class JvmTest {
        @Test void shouldCompile() {
          assert 10 * 2 == 20
        }
      }
    """

    file('src/androidTest/groovy/groovyx/test/AndroidTest.groovy') << """
      package groovyx.test

      import android.support.test.runner.AndroidJUnit4
      import android.test.suitebuilder.annotation.SmallTest
      import groovy.transform.CompileStatic
      import org.junit.Before
      import org.junit.Test
      import org.junit.runner.RunWith

      @RunWith(AndroidJUnit4)
      @SmallTest
      @CompileStatic
      class AndroidTest {
        @Test void shouldCompile() {
          assert 5 * 2 == 10
        }
      }
    """

    when:
    runWithVersion gradleVersion, 'assemble', 'test'

    then:
    noExceptionThrown()
    file('build/outputs/apk/test-app-debug.apk').exists()
    file('build/intermediates/classes/debug/groovyx/test/MainActivity.class').exists()
    file('build/intermediates/classes/debug/groovyx/test/SimpleTest.class').exists()
    file('build/intermediates/classes/androidTest/debug/groovyx/test/AndroidTest.class').exists()
    file('build/intermediates/classes/test/debug/groovyx/test/JvmTest.class').exists()
    file('build/intermediates/classes/test/release/groovyx/test/JvmTest.class').exists()

    where:
    // test common configs that touches the different way to access the classpath
    kotlinVersion | androidPluginVersion | gradleVersion | googleMavenRepo
    '1.1.2'       | '1.5.0'              | '2.10'        | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '1.5.0'              | '2.10'        | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '1.5.0'              | '2.11'        | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '1.5.0'              | '2.11'        | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '1.5.0'              | '2.12'        | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '1.5.0'              | '2.12'        | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '2.0.0'              | '2.13'        | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '2.0.0'              | '2.13'        | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '2.1.2'              | '2.14'        | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '2.1.2'              | '2.14'        | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '2.2.0'              | '2.14.1'      | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '2.2.0'              | '2.14.1'      | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '2.2.0'              | '3.0'         | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '2.2.0'              | '3.0'         | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '2.2.0'              | '3.1'         | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '2.2.0'              | '3.1'         | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '2.2.3'              | '3.2'         | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '2.2.3'              | '3.2'         | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '2.3.0'              | '3.3'         | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '2.3.0'              | '3.3'         | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '2.3.0'              | '3.4'         | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '2.3.0'              | '3.4'         | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '2.3.1'              | '3.5'         | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '2.3.1'              | '3.5'         | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '2.3.2'              | '3.5'         | "maven { 'https://maven.google.com' }"
    '1.1.51'      | '2.3.2'              | '3.5'         | "maven { 'https://maven.google.com' }"
    '1.1.2'       | '2.3.3'              | '4.2'         | "google()"
    '1.1.51'      | '2.3.3'              | '4.2'         | "google()"
    '1.1.2'       | '3.0.0-rc1'          | '4.2'         | "google()"
    '1.1.51'      | '3.0.0-rc1'          | '4.2'         | "google()"
  }
}
