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

// import org.funktionale.either.Either
// import org.funktionale.either.eitherTry
import java.io.IOException

// fun<T> try(block: () -> T): TryResult {
//
// }

// fun <L, R> Either<L, R>.ifError(block: (e: L) -> Unit): Any? {
//    if (this is Either.Left<L, R>) {
//        block(this.l)
//        return null
//    }
//    return true
// }

inline fun <T> returnIfThrows(block: () -> T) {
    try {
        block()
    } catch (t: Throwable) {
        println("caught exception")
        return
    }
}

fun doThrow(): Boolean {
    throw IOException()
}

fun doNotThrow(): Boolean = true

fun callOther() {
//    eitherTry { doNotThrow() }.ifError { e -> println("error $e") } ?: return
    returnIfThrows {
        doThrow()
    }
    println("after try")
}

fun main(args: Array<String>) {
//    eitherTry { doThrow() }
//        .fold({ err -> println("error $err")}, { s -> println("success $s")})
//    eitherTry { doNotThrow() }
//        .fold({ err -> println("error $err")}, { s -> println("success $s")})

//    val result = eitherTry { doThrow() }

//    eitherTry { doThrow() }.ifError { e -> println("Error!: $e") }
//    eitherTry { doNotThrow() }.ifError { println("Error!")}
    callOther()
}
