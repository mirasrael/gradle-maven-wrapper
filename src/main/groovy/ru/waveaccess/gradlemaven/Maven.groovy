package ru.waveaccess.gradlemaven

import org.gradle.api.Project

/**
 * Date: 02.07.2014
 * Time: 13:22
 */
class Maven {
    String version

    void configure(Project project) {
        if (version == null) {
            version = '3.2.2'
        }
    }

    void version(String version) {
        this.version = version
    }
}
