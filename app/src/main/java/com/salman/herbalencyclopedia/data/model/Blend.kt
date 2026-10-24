package com.salman.herbalencyclopedia.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Mirrors a "blends" (خلطات) document in Firestore. A blend is a mix of
 * existing herbs (referenced by id, same "herbs" collection) with its own
 * benefits/usage/warnings/notes, following the exact same schema style as
 * [Herb] so both stay consistent and admin-only-write like the rest of the
 * catalog.
 */
data class Blend @JvmOverloads constructor(
    @DocumentId
    val id: String = "",

    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",

    @get:PropertyName("herb_ids")
    @set:PropertyName("herb_ids")
    var herbIds: List<String> = emptyList(),

    @get:PropertyName("benefits")
    @set:PropertyName("benefits")
    var benefits: String = "",

    @get:PropertyName("usage")
    @set:PropertyName("usage")
    var usage: String = "",

    @get:PropertyName("warnings")
    @set:PropertyName("warnings")
    var warnings: String = "",

    @get:PropertyName("notes")
    @set:PropertyName("notes")
    var notes: String = "",

    @get:PropertyName("image_url")
    @set:PropertyName("image_url")
    var imageUrl: String? = null
)
