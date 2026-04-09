package cn.zy.mozhi.app;

import cn.zy.mozhi.domain.content.model.entity.DraftEntity;
import cn.zy.mozhi.types.enums.DraftStatusEnum;
import cn.zy.mozhi.types.exception.BaseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftEntityTest {

    @Test
    void should_allow_content_updates_only_for_editable_states() {
        DraftEntity draft = DraftEntity.createNew(7L, "Draft title", "Draft body");
        DraftEntity uploading = draft.transitionTo(DraftStatusEnum.UPLOADING);
        DraftEntity rejected = draft.transitionTo(DraftStatusEnum.PENDING_REVIEW).transitionTo(DraftStatusEnum.REJECTED);

        assertThatCode(() -> draft.withContent("Updated draft", "Updated body"))
                .doesNotThrowAnyException();
        assertThatCode(() -> uploading.withContent("Updated uploading", "Updated body"))
                .doesNotThrowAnyException();
        assertThatCode(() -> rejected.withContent("Updated rejected", "Updated body"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> draft.transitionTo(DraftStatusEnum.PENDING_REVIEW).withContent("Frozen", "Body"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("read only");
        assertThatThrownBy(() -> draft.transitionTo(DraftStatusEnum.PENDING_REVIEW)
                        .transitionTo(DraftStatusEnum.PUBLISHED)
                        .withContent("Published", "Body"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("read only");
        assertThatThrownBy(() -> draft.transitionTo(DraftStatusEnum.ARCHIVED).withContent("Archived", "Body"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("read only");
    }
}
