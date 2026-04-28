package org.telegram.tlrpc.telegram

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.ObjectCreationExpr
import java.io.File

object TelegramCodeParser {
    fun parse(files: List<File>): TelegramTlClasses {
        return TelegramTlClasses(files.map { findAllClasses(it) }.flatten().toSet())
    }

    private fun findAllClasses(javaFile: File): List<TelegramTlClass> {
        val parser = JavaParser()
        val result = parser.parse(javaFile)

        if (!result.isSuccessful || !result.result.isPresent) {
            return emptyList()
        }

        val cu: CompilationUnit = result.result.get()
        val packageName = cu.packageDeclaration
            .map { it.nameAsString }
            .orElse(null)


        val matchingClassNames = mutableListOf<TelegramTlClass>()

        val allClassLike = cu.findAll(ClassOrInterfaceDeclaration::class.java)

        for (cls in allClassLike) {
            val hasStaticIntConstructor = cls.fields.any { field ->
                field.isStatic &&
                field.commonType.toString() == "int" &&
                field.variables.any { it.nameAsString == "constructor" }
            }

            var constructorValue: Int? = null;
            if (hasStaticIntConstructor) {
                cls.fields.forEach { field ->
                    if (field.isStatic && field.commonType.toString() == "int") {
                        field.variables.forEach { variable ->
                            if (variable.nameAsString == "constructor") {
                                variable.initializer.ifPresent { expr ->
                                    expr.asIntegerLiteralExpr()?.let {
                                        constructorValue = it.asInt()
                                    }
                                }

                            }
                        }
                    }
                }
            }

            val staticDeserializeMethods = cls.methods.filter { method ->
                (method.nameAsString == "TLdeserialize" || method.nameAsString == "fromConstructor") && method.isStatic
            }
            val staticDeserializeCreations = staticDeserializeMethods.map(::findNewExpressionsInMethod).flatten().toSet().toList()
            val hasDeserializeMethod = staticDeserializeMethods.find { method ->
                    method.parameters.size >= 3 &&
                    method.parameters[0].type.toString() == "InputSerializedData" &&
                    method.parameters[1].type.toString() == "int" &&
                    method.parameters[2].type.toString() == "boolean" } != null

            val hasSerializeToStream = cls.methods.any { method ->
                method.nameAsString == "serializeToStream" &&
                method.isPublic &&
                method.parameters.size == 1 &&
                method.parameters[0].type.toString() == "OutputSerializedData" &&
                method.type.toString() == "void"
            }

            val hasReadParams = cls.methods.any { method ->
                method.nameAsString == "readParams" &&
                method.isPublic &&
                method.parameters.size == 2 &&
                method.parameters[0].type.toString() == "InputSerializedData" &&
                method.parameters[1].type.toString() == "boolean" &&
                method.type.toString() == "void"
            }

            val hasDeserializeResponse = cls.methods.any { method ->
                method.nameAsString == "deserializeResponse" &&
                method.isPublic &&
                method.type.asString() == "TLObject" &&
                method.parameters.size == 3 &&
                method.parameters[0].type.toString() == "InputSerializedData" &&
                method.parameters[1].type.toString() == "int" &&
                method.parameters[2].type.toString() == "boolean"
            }

            if (hasStaticIntConstructor || hasDeserializeMethod || hasSerializeToStream || hasReadParams || hasDeserializeResponse) {
                matchingClassNames.add(TelegramTlClass(
                    constructor = constructorValue?.toUInt(),

                    packageName = packageName,
                    fullName = getQualifiedName(cls),
                    name = cls.nameAsString,

                    canSerialize = hasSerializeToStream,
                    canDeserialize = hasReadParams,
                    canReadResponse = hasDeserializeResponse,
                    canStaticDeserialize = hasDeserializeMethod,
                    staticDeserializeCreations = staticDeserializeCreations
                ))
            }
        }

        return matchingClassNames
    }

    private fun findNewExpressionsInMethod(method: MethodDeclaration): List<String> {
        return method.findAll(ObjectCreationExpr::class.java)
            .map { it.typeAsString }
    }

    private fun getQualifiedName(cls: ClassOrInterfaceDeclaration): String {
        val parent = cls.parentNode
        return if (parent.isPresent && parent.get() is ClassOrInterfaceDeclaration) {
            getQualifiedName(parent.get() as ClassOrInterfaceDeclaration) + "." + cls.nameAsString
        } else {
            cls.nameAsString
        }
    }
}