package com.abilitycombat.ability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 능력 메타데이터를 정의하는 어노테이션
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface AbilityManifest {

    /**
     * 능력 종족
     */
    enum Species {
        SPECIAL("§e특별"),
        HUMAN("§f인간"),
        GOD("§c신"),
        DEMIGOD("§c반신§7반인"),
        ANIMAL("§2동물"),
        UNDEAD("§c언데드"),
        OTHERS("§8기타");

        private final String display;

        Species(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }

    /**
     * 능력 이름
     */
    String name();

    /**
     * 능력 종족
     */
    Species species();

    /**
     * 능력 설명 (여러 줄)
     */
    String[] explain() default {};

    /**
     * 능력 요약 설명 (GUI용)
     */
    String[] summarize() default {};
}
