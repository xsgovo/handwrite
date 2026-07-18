package com.xsgovo.handwrite.core.model

sealed interface DomainFailure {
    data object StorageFull : DomainFailure
    data object DocumentNotFound : DomainFailure
    data object PageNotFound : DomainFailure
    data object LastPageCannotBeDeleted : DomainFailure
    data object NameConflict : DomainFailure
    data object DatabaseUnavailable : DomainFailure
    data class UnsupportedPackageVersion(val version: Int) : DomainFailure
    data class InvalidPackage(val reason: String) : DomainFailure
    data object ExportTargetUnavailable : DomainFailure
}

sealed interface DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>

    data class Failure(val error: DomainFailure) : DomainResult<Nothing>
}
