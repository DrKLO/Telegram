package org.telegram.tlrpc

import org.telegram.tlrpc.models.*

object SchemeChangelogGen {
    fun getChangelog(old: TlSchemeWithMeta, new: TlSchemeWithMeta): TlSchemeChangelog {

        return TlSchemeChangelog(
            methods = getMethodsChangelog(old.methodsByName, new.methodsByName),
            constructors = getConstructorChangelog(old.constructorsByName, new.constructorsByName)
        )
    }

    private fun getParamsChangelog(oldList: List<TlParam>, newList: List<TlParam>): List<TlParamChangelog> {
        val old = oldList.associateBy { it.name }
        val new = newList.associateBy { it.name }

        val removed = old.keys - new.keys
        val added = new.keys - old.keys
        val unchanged = old.keys intersect new.keys

        val changelog = mutableListOf<TlParamChangelog>()
        changelog.addAll(added.map { TlParamChangelog.Added(it, newType = new[it]!!.type) }.toList())
        changelog.addAll(removed.map { TlParamChangelog.Removed(it, oldType = old[it]!!.type) }.toList())

        for (param in unchanged) {
            val oldParam = old[param]!!
            val newParam = new[param]!!

            if (oldParam != newParam) {
                changelog.add(TlParamChangelog.Changed(param, oldType = oldParam.type, newType = newParam.type))
            }
        }

        return changelog
    }

    private fun getConstructorChangelog(old: Map<TlTypeName, TlObject>, new: Map<TlTypeName, TlObject>): List<TlConstructorChangelog> {
        val removed = old.keys - new.keys
        val added = new.keys - old.keys
        val unchanged = old.keys intersect new.keys

        val changelog = mutableListOf<TlConstructorChangelog>()
        changelog.addAll(added.map { TlConstructorChangelog.Added(it) }.toList())
        changelog.addAll(removed.map { TlConstructorChangelog.Removed(it) }.toList())

        for (constructor in unchanged) {
            val oldConstructor = old[constructor]!!
            val newConstructor = new[constructor]!!

            if (oldConstructor != newConstructor) {
                changelog.add(TlConstructorChangelog.Changed(constructor, changelog = getParamsChangelog(oldConstructor.params.list, newConstructor.params.list)))
            }
        }

        return changelog
    }

    private fun getMethodsChangelog(old: Map<String, TlObject>, new: Map<String, TlObject>): List<TlMethodChangelog> {
        val removed = old.keys - new.keys
        val added = new.keys - old.keys
        val unchanged = old.keys intersect new.keys

        val changelog = mutableListOf<TlMethodChangelog>()
        changelog.addAll(added.map { TlMethodChangelog.Added(it) }.toList())
        changelog.addAll(removed.map { TlMethodChangelog.Removed(it) }.toList())

        for (method in unchanged) {
            val oldMethod = old[method]!!
            val newMethod = new[method]!!

            if (oldMethod != newMethod) {
                changelog.add(TlMethodChangelog.Changed(method = method, changelog = getParamsChangelog(oldMethod.params.list, newMethod.params.list)))
            }
        }

        return changelog
    }
}
