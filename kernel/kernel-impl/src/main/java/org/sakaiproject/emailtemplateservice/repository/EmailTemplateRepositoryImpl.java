package org.sakaiproject.emailtemplateservice.impl.repository;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

import java.util.List;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import org.sakaiproject.emailtemplateservice.api.model.EmailTemplate;
import org.sakaiproject.emailtemplateservice.api.repository.EmailTemplateRepository;
import org.sakaiproject.springframework.data.SpringCrudRepositoryImpl;

public class EmailTemplateRepositoryImpl extends SpringCrudRepositoryImpl<EmailTemplate, Long> implements EmailTemplateRepository {

    @Transactional
    public Optional<EmailTemplate> findByKeyAndLocale(String key, String locale) {

        Session session = sessionFactory.getCurrentSession();
        List<EmailTemplate> templates = session.createCriteria(EmailTemplate.class)
            .add(Restrictions.eq("key", key))
            .add(Restrictions.eq("locale", locale)).list();
        return templates.size() > 0 ? Optional.of(templates.get(0)) : Optional.empty();
    }

    @Transactional
    public Optional<EmailTemplate> findByKeyAndLocaleAndNotTemplateId(String key, String locale, Long templateId) {

        Session session = sessionFactory.getCurrentSession();
        Criteria criteria = session.createCriteria(EmailTemplate.class)
                .add(Restrictions.eq("key", key))
                .add(Restrictions.eq("locale", locale));

        if (templateId != null) {
            criteria.add(Restrictions.ne("id", templateId));
        }

        List<EmailTemplate> templates = criteria.list();
        return templates.size() > 0 ? Optional.of(templates.get(0)) : Optional.empty();
    }
}
