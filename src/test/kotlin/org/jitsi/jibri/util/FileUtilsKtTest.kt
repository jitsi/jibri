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
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

internal class FileUtilsKtTest : ShouldSpec() {
    override fun isInstancePerTest(): Boolean = true
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
        "createIfDoesNotExist" {
            "with the proper permissions" {
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
            "without permissions to create in a directory" {
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
        "deleteRecursively" {
            "for a directory" {
                val baseDir = createPath("/dir")
                "that's empty" {
                    "with perms" {
                        should("succeed") {
                            deleteRecursively(baseDir) shouldBe true
                            Files.exists(baseDir) shouldBe false
                        }
                    }
                    "without perms" {
                        setPerms("r--r--r--", baseDir)
                        should("fail") {
                            deleteRecursively(baseDir) shouldBe false
                            Files.exists(baseDir) shouldBe true
                        }
                    }
                }
                "with nested dirs" {
                    val subDir = createPath(baseDir.resolve("subDir"))
                    "with perms" {
                        should("succeed") {
                            deleteRecursively(baseDir) shouldBe true
                            Files.exists(baseDir) shouldBe false
                        }
                        "with nested files" {
                            createPath(subDir.resolve("file.txt"))
                            should("succeed") {
                                deleteRecursively(baseDir) shouldBe true
                                Files.exists(baseDir) shouldBe false
                            }
                        }
                    }
                    "without perms on the subDir" {
                        setPerms("r--r--r--", subDir)
                        should("fail") {
                            deleteRecursively(baseDir) shouldBe false
                            Files.exists(baseDir) shouldBe true
                        }
                    }
                }
                "with files of different permissions" {
                    val readOnlyFile = createPath(baseDir.resolve("file1.txt")).withPerms("r--r--r--")
                    val normalFile = createPath(baseDir.resolve("file2.txt"))
                    // This test is disabled because the memory file system library doesn't seem to support
                    // enforcing file permissions here (even though the file is read-only, it can
                    // still be deleted).  I'm leaving it because I hope to try and path the lib soon.
                    should("fail, but delete any files it can").config(enabled = false) {
                        deleteRecursively(baseDir) shouldBe false
                        Files.exists(readOnlyFile) shouldBe true
                        Files.exists(normalFile) shouldBe false
                    }
                }
            }
        }
    }
}
