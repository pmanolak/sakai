/******************************************************************************
 * Copyright 2015 sakaiproject.org Licensed under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package org.sakaiproject.rubrics.api.beans;

import org.sakaiproject.rubrics.api.model.Rating;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@NoArgsConstructor
public class RatingTransferBean {

    private Long id;
    private Long criterionId;
    private String description;
    private Double points;
    private Double weightedPoints;
    private String title;

    public RatingTransferBean(Rating rating) {
        Objects.requireNonNull(rating, "rating must not be null in constructor");
        id = rating.getId();
        criterionId = rating.getCriterion().getId();
        description = rating.getDescription();
        points = rating.getPoints();
        weightedPoints = 0D;
        title = rating.getTitle();
    }
}
