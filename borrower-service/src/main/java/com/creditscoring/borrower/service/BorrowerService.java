package com.creditscoring.borrower.service;

import com.creditscoring.borrower.entity.Company;
import com.creditscoring.borrower.entity.Industry;
import com.creditscoring.borrower.repository.CompanyRepository;
import com.creditscoring.borrower.repository.IndustryRepository;
import com.creditscoring.common.cache.ProtoCache;
import com.creditscoring.common.error.AppException;
import com.creditscoring.common.security.Actor;
import com.creditscoring.common.security.Roles;
import com.creditscoring.contracts.CompanyList;
import com.creditscoring.contracts.IndustryList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Бизнес-логика компаний. Справочник отраслей и карточки компаний - часто читаемые и редко изменяемые данные,
 * поэтому кешируются в Redis (cache-aside); при изменении профиля запись кеша сбрасывается.
 */
@Service
public class BorrowerService {
    private static final Logger log = LoggerFactory.getLogger(BorrowerService.class);
    private static final Pattern PHONE = Pattern.compile("^[+0-9][0-9 ()\\-]{5,29}$");
    static final String INDUSTRIES_KEY = "borrower:industries";

    private final CompanyRepository companies;
    private final IndustryRepository industries;
    private final ProtoCache cache;

    public BorrowerService(CompanyRepository companies, IndustryRepository industries, ProtoCache cache) {
        this.companies = companies;
        this.industries = industries;
        this.cache = cache;
    }

    public IndustryList listIndustries() {
        return cache.getOrLoad(INDUSTRIES_KEY, IndustryList.getDefaultInstance(), Duration.ofHours(1), () ->
                IndustryList.newBuilder().addAllItems(industries.findAll().stream().map(Mapper::toProto).toList()).build());
    }

    public com.creditscoring.contracts.Company getCompany(Actor actor, UUID id) {
        checkOwnership(actor, id);
        return cache.getOrLoad(companyKey(id), com.creditscoring.contracts.Company.getDefaultInstance(), Duration.ofMinutes(10), () ->
                Mapper.toProto(companies.findById(id).orElseThrow(() -> AppException.notFound("Компания"))));
    }

    @Transactional(readOnly = true)
    public CompanyList getCompanies(Actor actor, List<UUID> ids) {
        if (actor.isBorrower()) {
            // заёмщик может запрашивать только свою компанию
            ids = ids.stream().filter(i -> i.toString().equals(actor.companyId())).toList();
        }
        return CompanyList.newBuilder().addAllItems(companies.findByIdIn(ids).stream().map(Mapper::toProto).toList()).build();
    }

    /** Бизнес-операция: заёмщик обновляет контакты своей компании. */
    @Transactional
    public com.creditscoring.contracts.Company updateProfile(Actor actor, UUID companyId, String address, String phone) {
        actor.requireRole(Roles.BORROWER);
        checkOwnership(actor, companyId);
        if (address != null && address.length() > 300) {
            throw AppException.badRequest("Адрес не должен превышать 300 символов");
        }
        if (phone != null && !phone.isBlank() && !PHONE.matcher(phone).matches()) {
            throw AppException.badRequest("Некорректный формат телефона");
        }
        Company c = companies.findById(companyId).orElseThrow(() -> AppException.notFound("Компания"));
        if (address != null && !address.isBlank()) {
            c.setAddress(address.trim());
        }
        if (phone != null && !phone.isBlank()) {
            c.setPhone(phone.trim());
        }
        companies.flush();
        cache.evict(companyKey(companyId));
        log.info("BUSINESS company.updateProfile company={} by={}", companyId, actor.email());
        return Mapper.toProto(c);
    }

    private static void checkOwnership(Actor actor, UUID companyId) {
        if (actor.isBorrower() && !companyId.toString().equals(actor.companyId())) {
            throw AppException.forbidden("Доступ к чужой компании запрещён");
        }
    }

    static String companyKey(UUID id) {
        return "borrower:company:" + id;
    }

    /** Преобразование сущностей в сообщения контракта: наружу сущности БД не выходят. */
    static final class Mapper {
        static com.creditscoring.contracts.Industry toProto(Industry i) {
            return com.creditscoring.contracts.Industry.newBuilder().setCode(i.getCode()).setName(i.getName()).build();
        }

        static com.creditscoring.contracts.Company toProto(Company c) {
            return com.creditscoring.contracts.Company.newBuilder()
                    .setId(c.getId().toString()).setName(c.getName()).setUnp(c.getUnp())
                    .setIndustryCode(c.getIndustry().getCode()).setIndustry(toProto(c.getIndustry()))
                    .setAddress(nz(c.getAddress())).setPhone(nz(c.getPhone())).build();
        }

        private static String nz(String s) {
            return s == null ? "" : s;
        }
    }
}
