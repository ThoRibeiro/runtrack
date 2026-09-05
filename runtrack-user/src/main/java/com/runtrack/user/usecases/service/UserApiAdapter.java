package com.runtrack.user.usecases.service;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.FederatedProfile;
import com.runtrack.user.NewUser;
import com.runtrack.user.RunnerMass;
import com.runtrack.user.UserApi;
import com.runtrack.user.UserSummary;
import com.runtrack.user.usecases.port.UserRepository;
import com.runtrack.user.usecases.model.profile.Email;
import com.runtrack.user.usecases.model.profile.Handle;
import com.runtrack.user.usecases.model.profile.User;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** L'implémentation du contrat public du module. Ne fait que traduire, jamais décider. */
@Service("userApiAdapter")
class UserApiAdapter implements UserApi {

    private final UserRepository users;
    private final UserAccounts accounts;

    UserApiAdapter(UserRepository users, UserAccounts accounts) {
        this.users = users;
        this.accounts = accounts;
    }

    @Override
    public UserId register(NewUser newUser) {
        return accounts.register(
                new Handle(newUser.handle()), new Email(newUser.email()), newUser.displayName());
    }

    @Override
    public boolean ensureProfile(UserId id, FederatedProfile profile) {
        return accounts.provisionFederated(id, profile);
    }

    @Override
    public void confirmEmail(UserId id) {
        accounts.verifyEmail(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserId> idOfEmail(String email) {
        return users.findByEmail(new Email(email)).map(User::id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UserId id) {
        return users.findById(id).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> summary(UserId id) {
        return users.findById(id).map(UserApiAdapter::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UserId, UserSummary> summaries(Collection<UserId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream()
                .map(UserApiAdapter::toSummary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AudienceScope> accountScope(UserId id) {
        return users.findById(id).map(User::accountScope);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RunnerMass> massOf(UserId id) {
        return users.findById(id)
                .map(User::physiology)
                .filter(physiology -> physiology.weightKilograms().isPresent())
                .map(physiology -> new RunnerMass(physiology.weightKilograms().getAsDouble()));
    }

    private static UserSummary toSummary(User user) {
        return new UserSummary(user.id(), user.handle().value(), user.displayName(), user.avatarUrl());
    }
}
