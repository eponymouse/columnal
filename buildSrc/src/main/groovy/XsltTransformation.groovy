/**
 * Taken from https://github.com/eerohele/saxon-gradle 
 *
 * This is the original license (which was shipped without year or name):
 *
 MIT License

 Copyright (c) [year] [fullname]

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */
import net.sf.saxon.Transform
import org.gradle.workers.WorkAction

@SuppressWarnings('AbstractClassWithoutAbstractMethod')
abstract class XsltTransformation implements WorkAction<XsltWorkParameters> {
    @Override
    void execute() {
        // println("Using Saxon version ${net.sf.saxon.Version.productVersion}")
        println("Arguments: " + parameters.arguments.get().join("\n"))
        new Transform().doTransform(parameters.arguments.get() as String[], '')
    }
}