/*
 * Copyright 2014-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.edeandrea.xjcplugin.plugin

import com.github.edeandrea.xjcplugin.AbstractUnitTests
import com.github.edeandrea.xjcplugin.domain.Schema
import com.github.edeandrea.xjcplugin.domain.XjcExtension
import com.github.edeandrea.xjcplugin.type.Xjc
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskCollection
import org.junit.jupiter.api.Test
import java.io.File

internal class XjcPluginUnitTests : AbstractUnitTests() {
	private fun createProject() : Project {
		val project = getProject("XjcPlugin")
		project.pluginManager.apply(XjcPlugin::class.java)

		return project
	}

	@Test
	fun `java plugin is applied`() {
		assertThat(createProject().plugins.findPlugin("java")).isNotNull
	}

	@Test
	fun `XJC plugin is applied`() {
		assertThat(createProject().plugins.findPlugin(XjcPlugin::class.java)).isNotNull
	}

	@Test
	fun `xjc configuration created`() {
		assertThat(createProject().configurations.findByName("xjc")).isNotNull
	}

	@Test
	fun `xjcGeneration task exists and is configured properly`() {
		assertThat(createProject().tasks.findByName("xjcGeneration")).isNotNull()
	}

	@Test
	fun `xjcGeneration task has correct dependencies`() {
		val project = createProject()
		project.tasks.register("generateSomeSchema", Xjc::class.java) { task ->
			task.description = "Generate the sources from the schema"
			task.group = "Code Generation"
			task.javaPackageName = "com.some.project.generated.jaxb"
			task.schemaFile = File(tempFolder, "schema.xsd")
		}

		runProjectAfterEvaluate(project)
		val xjcGenerationTask = project.tasks.findByName("xjcGeneration")
		val dependencies = xjcGenerationTask?.dependsOn
		val taskDependencies = dependencies?.filterIsInstance<TaskCollection<*>>()

		assertThat(xjcGenerationTask).isNotNull()
		assertThat(dependencies).isNotEmpty
		assertThat(taskDependencies)
			.isNotEmpty
			.hasSize(1)

		assertThat(taskDependencies?.first()?.findByName("generateSomeSchema")).isNotNull()
	}

	@Test
	fun `xjcGeneration extension is there`() {
		assertThat(createProject().extensions.findByName("xjcGeneration"))
			.isNotNull
			.isInstanceOf(XjcExtension::class.java)
	}

	@Test
	fun `xjcGeneration extension configures properly`() {
		val schema = Schema("someSchema")
		schema.schemaFile = "some-schema.xsd"
		schema.javaPackageName = "some.package"

		val project = createProject()
		project.extensions.getByType(XjcExtension::class.java).schemas.add(schema)

		assertThatCode {
			runProjectAfterEvaluate(project)
		}
			.doesNotThrowAnyException()

		assertThat(project.tasks.withType(Xjc::class.java).size).isEqualTo(1)
		assertThat(project.tasks.findByName("schemaGen_some-package")).isNotNull()
	}

	@Test
	fun `xjcGeneration extension allows task name to be specified`() {
		val schema = Schema("someSchema")
		schema.schemaFile = "some-schema.xsd"
		schema.javaPackageName = "some.package"
		schema.taskName = "someSchemaTask"

		val project = createProject()
		project.extensions.getByType(XjcExtension::class.java).schemas.add(schema)

		assertThatCode {
			runProjectAfterEvaluate(project)
		}
			.doesNotThrowAnyException()

		assertThat(project.tasks.withType(Xjc::class.java).size).isEqualTo(1)
		assertThat(project.tasks.findByName("someSchemaTask")).isNotNull()
	}

	@Test
	fun `xjcGeneration extension allows task description to be specified`() {
		val schema = Schema("someSchema")
		schema.schemaFile = "some-schema.xsd"
		schema.javaPackageName = "some.package"
		schema.taskName = "someSchemaTask"
		schema.description = "Some description"

		val project = createProject()
		project.extensions.getByType(XjcExtension::class.java).schemas.add(schema)

		assertThatCode {
			runProjectAfterEvaluate(project)
		}
			.doesNotThrowAnyException()

		assertThat(project.tasks.withType(Xjc::class.java).size).isEqualTo(1)
		assertThat(project.tasks.findByName("someSchemaTask"))
			.isNotNull()
			.extracting("description")
			.isEqualTo("Some description")
	}

	@Test
	fun `xjcGeneration doesn't allow 2 tasks of the same name`() {
		val schema1 = Schema("schema1")
		schema1.schemaFile = "some-schema1.xsd"
		schema1.javaPackageName = "some.package1"
		schema1.taskName = "someSchemaTask"

		val schema2 = Schema("schema2")
		schema2.schemaFile = "some-schema2.xsd"
		schema2.javaPackageName = "some.package2"
		schema2.taskName = "someSchemaTask"

		val project = createProject()
		project.extensions.getByType(XjcExtension::class.java).schemas.addAll(listOf(schema1, schema2))

		assertThatExceptionOfType(InvalidUserDataException::class.java)
			.isThrownBy { runProjectAfterEvaluate(project) }
			.withMessage("Cannot add task 'someSchemaTask' as a task with that name already exists.")
			.withNoCause()
	}

	@Test
	fun `xjcGeneration sourceSet configurations ok`() {
		val schema1 = Schema("schema1")
		schema1.schemaFile = "some-schema1.xsd"
		schema1.javaPackageName = "some.package1"

		val schema2 = Schema("schema2")
		schema2.schemaFile = "some-schema2.xsd"
		schema2.javaPackageName = "some.package2"
		schema2.sourceSet = "test"

		val project = createProject()
		val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
		project.extensions.getByType(XjcExtension::class.java).schemas.addAll(listOf(schema1, schema2))

		runProjectAfterEvaluate(project)

		val xjcTasks = project.tasks.withType(Xjc::class.java)

		assertThat(xjcTasks)
			.isNotEmpty
			.hasSize(2)

		assertThat(xjcTasks.first())
			.isNotNull()
			.extracting(
				"name",
				"sourceSet"
			)
			.containsExactly(
				"schemaGen_some-package1",
				sourceSets.getByName("main")
			)

		assertThat(xjcTasks.last())
			.isNotNull()
			.extracting(
				"name",
				"sourceSet"
			)
			.containsExactly(
				"schemaGen_some-package2",
				sourceSets.getByName("test")
			)
	}

	@Test
	fun `xjcGeneration extension is missing required schemaFile property`() {
		val project = createProject()
		project.extensions.getByType(XjcExtension::class.java).schemas.add(Schema("schema"))

		assertThatExceptionOfType(UninitializedPropertyAccessException::class.java)
			.isThrownBy { runProjectAfterEvaluate(project) }
			.withMessage("lateinit property schemaFile has not been initialized")
			.withNoCause()
	}
}