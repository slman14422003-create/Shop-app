package com.salman.herbalencyclopedia.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Mirrors the "herbs" collection in Firestore, same schema used by the
 * original web app (js/firebase-sync.js) so both clients stay compatible.
 */
data class Herb @JvmOverloads constructor(
    @DocumentId
    val id: String = "",

    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",

    @get:PropertyName("category_id")
    @set:PropertyName("category_id")
    var categoryId: String? = null,

    @get:PropertyName("benefits")
    @set:PropertyName("benefits")
    var benefits: String = "",

    @get:PropertyName("warnings")
    @set:PropertyName("warnings")
    var warnings: String = "",

    @get:PropertyName("harms")
    @set:PropertyName("harms")
    var harms: String = "",

    @get:PropertyName("usage")
    @set:PropertyName("usage")
    var usage: String = "",

    @get:PropertyName("notes")
    @set:PropertyName("notes")
    var notes: String = "",

    @get:PropertyName("image_url")
    @set:PropertyName("image_url")
    var imageUrl: String? = null
) {
    // Empty constructor already covered by default values (needed by Firestore).
}
