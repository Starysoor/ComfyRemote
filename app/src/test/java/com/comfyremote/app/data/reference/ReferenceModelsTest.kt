package com.comfyremote.app.data.reference

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceModelsTest {

    @Test
    fun commonWorkflowInputsInferSemanticRoles() {
        assertEquals(ReferenceRole.START_FRAME, inferReferenceRole("start_image"))
        assertEquals(ReferenceRole.START_FRAME, inferReferenceRole("ref_image_1", "首帧"))
        assertEquals(ReferenceRole.END_FRAME, inferReferenceRole("last_frame"))
        assertEquals(ReferenceRole.CHARACTER_REFERENCE, inferReferenceRole("character_ref"))
        assertEquals(ReferenceRole.SCENE_REFERENCE, inferReferenceRole("scene_image"))
        assertEquals(ReferenceRole.STYLE_REFERENCE, inferReferenceRole("style_ref"))
        assertEquals(ReferenceRole.OUTFIT_REFERENCE, inferReferenceRole("outfit"))
        assertEquals(ReferenceRole.COMPOSITION_REFERENCE, inferReferenceRole("composition"))
        assertEquals(ReferenceRole.ACTION_REFERENCE, inferReferenceRole("pose_action"))
    }

    @Test
    fun unknownInputsStayAuto() {
        assertEquals(ReferenceRole.AUTO, inferReferenceRole("image"))
        assertEquals(ReferenceRole.AUTO, inferReferenceRole(""))
    }

    @Test
    fun storageRoundTripIsCaseInsensitive() {
        assertEquals(ReferenceRole.END_FRAME, ReferenceRole.fromStorage("end_frame"))
        assertEquals(ReferenceRole.END_FRAME, ReferenceRole.fromStorage("END_FRAME"))
        assertEquals(ReferenceRole.AUTO, ReferenceRole.fromStorage("nonsense"))
    }
}
