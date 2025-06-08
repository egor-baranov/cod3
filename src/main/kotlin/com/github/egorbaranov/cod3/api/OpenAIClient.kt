package com.github.egorbaranov.jetbrainsaicodeinspectionplugin.api

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class OpenAIClient(
    private val project: Project
) {


    companion object {

        fun getInstance(project: Project): OpenAIClient = project.service()

        private val logger = Logger.getInstance(OpenAIClient::class.java)
    }
}