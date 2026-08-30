package com.xsgovo.handwrite.core.model

sealed interface DomainFailure {
    data object StorageFull : DomainFailure
    data object DocumentNotFound : DomainFailure
    data object PageNotFound : DomainFailure
    data object DatabaseUnavailable : DomainFailure
    data object ResourceNotFound : DomainFailure
    data object InvalidResource : DomainFailure
}

sealed interface DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>

    data class Failure(val error: DomainFailure) : DomainResult<Nothing>
}
