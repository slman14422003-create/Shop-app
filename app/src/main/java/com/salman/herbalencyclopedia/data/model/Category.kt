package com.salman.herbalencyclopedia.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class Category @JvmOverloads constructor(
    @DocumentId
    val id: String = "",

    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",

    @get:PropertyName("icon")
    @set:PropertyName("icon")
    var icon: String? = null
)
