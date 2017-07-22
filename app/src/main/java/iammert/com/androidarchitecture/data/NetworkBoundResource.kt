package iammert.com.androidarchitecture.data

import android.annotation.SuppressLint
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.os.AsyncTask
import android.support.annotation.MainThread
import android.support.annotation.WorkerThread
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.lang.UnsupportedOperationException

abstract class NetworkBoundResource<ResultType, RequestType> {

    private val result = MediatorLiveData<Resource<ResultType>>()

    init {
        result.value = Resource.loading<ResultType>(null)
        val dbSource = loadFromDb()
        result.addSource(dbSource) { data ->
            result.removeSource(dbSource)
            if (shouldFetch(data))
                fetchFromNetwork(dbSource)
            else
                result.addSource(dbSource) { newData ->
                    newData ?: throw UnsupportedOperationException() //Todo 좀 더 코드를 이해한 뒤 수정
                    result.setValue(Resource.success(newData))
                }
        }
    }

    private fun fetchFromNetwork(dbSource: LiveData<ResultType>) {
        result.addSource(dbSource) { newData -> result.setValue(Resource.loading(newData)) }
        createCall().enqueue(object : Callback<RequestType> {
            override fun onResponse(call: Call<RequestType>, response: Response<RequestType>) {
                result.removeSource(dbSource)
                saveResultAndReInit(response.body())
            }

            override fun onFailure(call: Call<RequestType>, t: Throwable) {
                onFetchFailed()
                result.removeSource(dbSource)
                result.addSource(dbSource) { newData -> result.setValue(Resource.error(t.message ?: "", newData)) }//Todo 좀 더 코드를 이해한 뒤 수정
            }
        })
    }

    @MainThread private fun saveResultAndReInit(response: RequestType) =
            object : AsyncTask<Void, Void, Void>() {

                override fun doInBackground(vararg voids: Void): Void? {
                    saveCallResult(response)
                    return null
                }

                override fun onPostExecute(aVoid: Void) =
                        result.addSource(loadFromDb()) { newData ->
                            newData ?: throw UnsupportedOperationException() //Todo 좀 더 코드를 이해한 뒤 수정
                            result.setValue(Resource.success(newData))
                        }

            }.execute()

    @WorkerThread protected abstract fun saveCallResult(item: RequestType)

    @MainThread protected fun shouldFetch(data: ResultType?): Boolean = true

    @MainThread protected abstract fun loadFromDb(): LiveData<ResultType>

    @MainThread protected abstract fun createCall(): Call<RequestType>

    @MainThread protected fun onFetchFailed() {
    }

    val asLiveData: LiveData<Resource<ResultType>> get() = result
}
