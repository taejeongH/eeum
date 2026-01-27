package org.ssafy.eeum.domain.family.entity;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@EqualsAndHashCode
public class SupporterId implements Serializable {
    private Integer user;
    private Long family;
}
