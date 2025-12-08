package org.example.taskbid.repositiry;

import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUser(User user);

}
