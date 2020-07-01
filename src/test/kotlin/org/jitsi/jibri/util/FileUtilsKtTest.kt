/*
 * Copyright @ 2018 Atlassian Pty Ltd
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
 *
 */

package org.jitsi.jibri.util

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

internal class FileUtilsKtTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val fs = MemoryFileSystemBuilder.newLinux().build()

    private fun setPerms(permsStr: String, p: Path) {
        val perms = PosixFilePermissions.fromString(permsStr)
        Files.setPosixFilePermissions(p, perms)
    }
    private fun createPath(pathStr: String): Path = createPath(fs.getPath(pathStr))
    private fun createPath(path: Path): Path {
        // Just check for the presence of a "." to determine if it's a file or a dir
        if (path.fileName.toString().contains(".")) {
            Files.createFile(path)
        } else {
            Files.createDirectories(path)
        }
        return path
    }
    private fun Path.withPerms(permString: String): Path {
        setPerms(permString, this)
        return this
    }

    init {
        context("createIfDoesNotExist") {
            context("with the proper permissions") {
                should("create a directory") {
                    val dir = fs.getPath("/xxx/dir")
                    createIfDoesNotExist(dir) shouldBe true
                    Files.exists(dir) shouldBe true
                }
                should("create nested directories") {
                    val dir = fs.getPath("/test/dir")
                    createIfDoesNotExist(dir) shouldBe true
                    Files.exists(dir) shouldBe true
                }
                should("return true if the dir already exists") {
                    val dir = fs.getPath("dir")
                    Files.createDirectory(dir)
                    createIfDoesNotExist(dir) shouldBe true
                    Files.exists(dir) shouldBe true
                }
            }
            context("without permissions to create in a directory") {
                val baseDir = fs.getPath("/noperms")
                Files.createDirectory(baseDir).withPerms("r--r--r--")
                should("fail to create a single dir") {
                    val newDir = baseDir.resolve("test")
                    createIfDoesNotExist(newDir) shouldBe false
                }
                should("fail to create nested dirs") {
                    val newDir = baseDir.resolve("test1/test2")
                    createIfDoesNotExist(newDir) shouldBe false
                }
            }
        }
    }
}
